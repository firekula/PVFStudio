package studio.pvf.intellij;
import studio.pvf.preview.SceneScript;
import studio.pvf.sceneedit.SourceDocument;
import java.io.IOException;
final class SceneAssetMetadata {
 static String describe(SceneResourceBrowser.Category category,String text) throws IOException {
  var source=SourceDocument.parse(text);if(!source.diagnostics().isEmpty())throw new IOException(source.diagnostics().getFirst().message());
  if(category==SceneResourceBrowser.Category.TILE){var tile=SceneScript.tile(text);return (tile.extended()?"扩展地块":"基础地块")+" · "+String.join("；",tile.warnings());}
  var names=source.sections("name");return names.isEmpty()?"":names.getLast().values().stream().map(SourceDocument.Token::value).collect(java.util.stream.Collectors.joining(" "));
 }
}
