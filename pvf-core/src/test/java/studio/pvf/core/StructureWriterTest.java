package studio.pvf.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructureWriterTest {
    @TempDir Path temporary;
    @Test void preservesExistingFilePrefixPathsWhileRejectingNewCollisions() throws Exception {
        try(var source=PvfArchive.open(fixture(PvfFormat.UUID,true))) {
            var paths=new LinkedHashMap<String,String>(); for(var e:source.entries()) paths.put(e.path(),e.path()); paths.put("added.etc","");
            Path output=temporary.resolve("prefix-result.pvf");PvfWriter.exportStructure(source,paths,Map.of("added.etc",new byte[]{1}),output);
            try(var result=PvfArchive.open(output)) { assertArrayEquals(source.readBytes("test/a.etc/sub.etc"),result.readBytes("test/a.etc/sub.etc")); }
        }
    }
    @Test void structuralExportHandlesAllContainersGapsPaddingLinksAndDeletedEntries() throws Exception {
        for(PvfFormat format:PvfFormat.values()) {
            Path source=fixture(format); byte[] untouched=Files.readAllBytes(source);
            try(var archive=PvfArchive.open(source)) {
                var origins=new LinkedHashMap<String,String>(); origins.put("new/longer/name.etc","test/a.etc"); origins.put("created/raw.bin","");
                Path output=temporary.resolve(format+"-result.pvf"); PvfWriter.exportStructure(archive,origins,Map.of("created/raw.bin",new byte[]{7,8,9}),output);
                try(var result=PvfArchive.open(output)) {
                    assertEquals(2,result.fileCount()); assertEquals(format,result.format());
                    assertArrayEquals(archive.readBytes("test/a.etc"),result.readBytes("new/longer/name.etc"));
                    assertArrayEquals(new byte[]{7,8,9},result.readBytes("created/raw.bin"));
                    assertThrows(java.io.IOException.class,()->result.entry("test/b.etc"));
                    if(format!=PvfFormat.LEGACY) assertEquals(archive.entry("test/a.etc").fileNumber(),result.entry("new/longer/name.etc").fileNumber());
                    assertEquals(0x55,Files.readAllBytes(output)[(int)result.bodyOffset()]&255);
                    assertEquals(0x77,Files.readAllBytes(output)[(int)result.archiveSize()-1]&255);
                }
                assertThrows(java.io.IOException.class,()->PvfWriter.exportStructure(archive,origins,Map.of("created/raw.bin",new byte[]{1}),output));
                var added=new LinkedHashMap<String,String>(); added.put("test/a.etc","test/a.etc"); added.put("test/b.etc","test/b.etc"); added.put("new/one.bin","");
                Path grown=temporary.resolve(format+"-grown.pvf"); PvfWriter.exportStructure(archive,added,Map.of("new/one.bin",new byte[0]),grown);
                try(var result=PvfArchive.open(grown)) { assertEquals(3,result.fileCount()); assertArrayEquals(new byte[0],result.readBytes("new/one.bin")); assertArrayEquals(archive.readBytes("test/b.etc"),result.readBytes("test/b.etc")); }
                Path empty=temporary.resolve(format+"-empty.pvf"); PvfWriter.exportStructure(archive,Map.of(),Map.of(),empty);
                try(var result=PvfArchive.open(empty)) { assertEquals(0,result.fileCount()); }
            }
            assertArrayEquals(untouched,Files.readAllBytes(source));
        }
    }
    private Path fixture(PvfFormat format) throws Exception {
        return fixture(format,false);
    }
    private Path fixture(PvfFormat format,boolean prefix) throws Exception {
        boolean legacy=format==PvfFormat.LEGACY; byte[] a="ab!".getBytes(StandardCharsets.US_ASCII), b="cd!!".getBytes(StandardCharsets.US_ASCII);
        byte[] pa="/test/a.etc".getBytes(StandardCharsets.US_ASCII),pb=(prefix?"/test/a.etc/sub.etc":"/test/b.etc").getBytes(StandardCharsets.US_ASCII);
        int treeSize=legacy?4*1028:((40+pa.length+pb.length+3)&~3)+4;
        int headerSize=legacy?32:format==PvfFormat.ACT1?27:56;
        byte[] header=new byte[headerSize],tree=new byte[treeSize]; ByteBuffer h=le(header),t=le(tree);
        int body=headerSize+treeSize, second=legacy?31:8;
        byte[] payload=new byte[legacy?62:16]; ByteBuffer p=le(payload); p.putInt(0x55555555);
        if(legacy) {
            h.putInt(1).putInt(1028).putInt(treeSize).putInt(2).putInt(2); t.put(pa).putInt(1024,body+4); t.position(1028);t.put(pb).putInt(1028+1024,body+second);
            p.position(4);p.putInt(1).putInt(1).putInt(body+4).putInt(3).putInt(3).putInt(body+second).put(new byte[]{0x6e,(byte)0xd5,0x21});
            p.position(second);p.putInt(1).putInt(1).putInt(body+second).putInt(4).putInt(4).putInt(body+second).put(new byte[]{0x70,(byte)0xd7,0x2d,(byte)0x95});
        } else {
            byte[] ap={a[0],a[1],a[2],0x33},bp=b.clone(); int mask=format==PvfFormat.UUID?0x81a79011:0;
            int ak=PvfIo.crc32(ap,format==PvfFormat.UUID?10:4),bk=PvfIo.crc32(bp,format==PvfFormat.UUID?20:second);
            if(format==PvfFormat.UUID) { h.putInt(36).put("01234567-0123-0123-0123-012345678901".getBytes(StandardCharsets.US_ASCII)).putInt(2).putInt(treeSize).putInt(0).putInt(2);
                t.putInt(10).putInt(pa.length).put(pa).putInt(a.length).putInt(ak).putInt(4);t.putInt(20).putInt(pb.length).put(pb).putInt(b.length).putInt(bk).putInt(second);
            } else { h.put("DNF_SCRIPT_PACK".getBytes(StandardCharsets.US_ASCII)).putInt(treeSize).putInt(0).putInt(2);
                t.putInt(10).putInt(4).putInt(a.length).putInt(ak).putInt(pa.length).put(pa);t.putInt(20).putInt(second).putInt(b.length).putInt(bk).putInt(pb.length).put(pb); }
            PvfIo.encrypt(ap,ak^mask);PvfIo.encrypt(bp,bk^mask);p.position(4);p.put(ap).put(bp);
            tree[tree.length-1]=0x66;int key=PvfIo.crc32(tree,2);h.putInt(headerSize-8,key);PvfIo.encrypt(tree,key^mask);
        }
        payload[payload.length-1]=0x77;
        byte[] all=new byte[body+payload.length];System.arraycopy(header,0,all,0,header.length);System.arraycopy(tree,0,all,header.length,tree.length);System.arraycopy(payload,0,all,body,payload.length);
        return Files.write(temporary.resolve(format+".pvf"),all);
    }
    private static ByteBuffer le(byte[] bytes){return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);}
}
