using System.Diagnostics;
using System.Text.Json;
using DFLegacy.Protocol;

if (args.Length is < 3 or > 4)
{
    Console.Error.WriteLine("Usage: ServerReader <original.pvf> <output.pvf> <paths.json> [changes.json]");
    return 2;
}

try
{
    var timer = Stopwatch.StartNew();
    var original = new PvfArchiveReader(args[0]);
    var output = new PvfArchiveReader(args[1]);
    var paths = JsonSerializer.Deserialize<string[]>(File.ReadAllText(args[2]))!;
    var replacements = args.Length == 4
        ? JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(args[3]))!
        : new Dictionary<string, string>();
    var manifestDirectory = args.Length == 4 ? Path.GetDirectoryName(Path.GetFullPath(args[3]))! : ".";
    if (original.FileCount != output.FileCount || paths.Length != original.FileCount)
        throw new InvalidDataException("File count does not match the independent path inventory");
    foreach (var path in paths)
    {
        var expected = replacements.TryGetValue(path, out var replacement)
            ? File.ReadAllBytes(Path.IsPathRooted(replacement) ? replacement : Path.Combine(manifestDirectory, replacement))
            : original.ReadAllBytes(path);
        if (!expected.AsSpan().SequenceEqual(output.ReadAllBytes(path)))
            throw new InvalidDataException($"Production reader mismatch: {path}");
    }
    Console.WriteLine(JsonSerializer.Serialize(new
    {
        status = "passed",
        reader = "DFLegacy.Protocol.PvfArchiveReader (linked original source)",
        entries_checked = paths.Length,
        expected_replacements = replacements.Count,
        elapsed_ms = timer.ElapsedMilliseconds,
        client_compatibility = "not_verified"
    }, new JsonSerializerOptions { WriteIndented = true }));
    return 0;
}
catch (Exception exception)
{
    Console.Error.WriteLine(exception.Message);
    return 1;
}
