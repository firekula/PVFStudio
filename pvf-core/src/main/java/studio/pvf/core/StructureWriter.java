package studio.pvf.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Structural exports rebuild the directory, retaining surviving entry identities and opaque data gaps. */
final class StructureWriter {
    private static final class Row {
        final String path; final PvfEntry original; final byte[] rawPath, replacement; final long number;
        long offset; int key, length;
        Row(String path, PvfEntry original, byte[] rawPath, byte[] replacement, long number) {
            this.path=path; this.original=original; this.rawPath=rawPath; this.replacement=replacement; this.number=number;
            length=replacement == null ? original.length() : replacement.length;
        }
    }
    static void export(PvfArchive source, Map<String,String> origins, Map<String,byte[]> changes, Path target) throws IOException {
        source.verifyUnchanged(); boolean legacy=source.format()==PvfFormat.LEGACY;
        List<Row> rows=new ArrayList<>(); Map<String,Row> survivors=new HashMap<>(); Set<Long> numbers=new HashSet<>();
        for(var e:source.entries()) numbers.add(e.fileNumber());
        long next=numbers.stream().mapToLong(Long::longValue).max().orElse(-1)+1;
        for(var item:origins.entrySet()) {
            String path=PvfArchive.normalizePath(item.getKey());
            if(!path.equals(item.getKey())) throw new IOException("Noncanonical output path: "+path);
            PvfEntry original=item.getValue().isEmpty()?null:source.entry(item.getValue());
            byte[] raw=original!=null && original.path().equals(path)?original.rawPath():PvfArchive.encodeNewPath(path);
            if(legacy && raw.length>1024) throw new IOException("Legacy path exceeds 1024 bytes: "+path);
            byte[] content=changes.get(path); if(content!=null) content=content.clone();
            if(original==null && content==null) throw new IOException("New entry has no content: "+path);
            long number=original==null?next:original.fileNumber();
            if(original==null) { while(numbers.contains(number&0xffffffffL)) number++; number&=0xffffffffL; numbers.add(number); next=number+1; }
            Row row=new Row(path,original,raw,content,number); rows.add(row);
            if(original!=null && survivors.put(original.path(),row)!=null) throw new IOException("Duplicate source identity: "+original.path());
        }
        for(String changed:changes.keySet()) if(!origins.containsKey(changed)) throw new IOException("Change targets a deleted entry: "+changed);
        for(String path:origins.keySet()) {
            for(int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1)) {
                String parent=path.substring(0,slash);
                if(origins.containsKey(parent) && (origins.get(parent).isEmpty() || !origins.get(path).startsWith(origins.get(parent)+"/")))
                    throw new IOException("File/directory collision: "+path);
            }
        }
        // Retain directory order for survivors; new entries follow it.
        rows.sort(Comparator.comparingInt(r->r.original==null?Integer.MAX_VALUE:r.original.index()));
        long oldCursor=0, cursor=0;
        for(PvfEntry e:source.physicalEntries()) {
            cursor+=e.offset()-oldCursor; Row row=survivors.get(e.path());
            if(row!=null) { row.offset=cursor; cursor+=legacy?24L+row.length:PvfIo.align4(row.length); }
            oldCursor=e.offset()+e.storedLength();
        }
        for(Row row:rows) if(row.original==null) { row.offset=cursor; cursor+=legacy?24L+row.length:PvfIo.align4(row.length); }
        long tail=source.archiveSize()-source.bodyOffset()-oldCursor, bodyLength=cursor+tail;
        byte[] oldTree=source.plainTree(); int used=0;
        if(legacy) used=source.fileCount()*1028;
        else for(PvfEntry e:source.entries()) used=Math.max(used,e.treePosition()+20+e.rawPath().length);
        byte[] opaque=Arrays.copyOfRange(oldTree,used,oldTree.length);
        long treeSize=opaque.length;
        for(Row row:rows) treeSize+=legacy?1028:20L+row.rawPath.length;
        treeSize=legacy?Math.max(1028,treeSize):Math.max(4,(treeSize+3)&~3L);
        if(treeSize>256L*1024*1024) throw new IOException("Output directory too large");
        byte[] tree=new byte[(int)treeSize], header=source.header(); long bodyOffset=header.length+treeSize;
        if(bodyLength+bodyOffset>0xffffffffL) throw new IOException("PVF exceeds uint32 offsets");
        ByteBuffer fields=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if(legacy) fields.putInt(8,tree.length).putInt(12,rows.size()).putInt(16,rows.size());
        else fields.putInt(source.treeKeyPosition()-4,tree.length).putInt(source.treeKeyPosition()+4,rows.size());
        Map<Long,Long> links=new HashMap<>(); links.put(0L,0L);
        for(Row row:rows) if(row.original!=null) links.put(source.bodyOffset()+row.original.offset(),bodyOffset+row.offset);
        Path temporary=Files.createTempFile(target.getParent(),".pvf-structure-",".tmp");
        try {
            try(FileChannel out=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                oldCursor=0; cursor=0;
                for(PvfEntry e:source.physicalEntries()) {
                    long gap=e.offset()-oldCursor;
                    PvfIo.copy(source.channel(),source.bodyOffset()+oldCursor,out,bodyOffset+cursor,gap); cursor+=gap;
                    Row row=survivors.get(e.path());
                    if(row!=null) { writeRow(source,row,out,bodyOffset,legacy,links); cursor=row.offset+(legacy?24L+row.length:PvfIo.align4(row.length)); }
                    oldCursor=e.offset()+e.storedLength();
                }
                for(Row row:rows) if(row.original==null) { writeRow(source,row,out,bodyOffset,legacy,links); cursor=row.offset+(legacy?24L+row.length:PvfIo.align4(row.length)); }
                PvfIo.copy(source.channel(),source.bodyOffset()+oldCursor,out,bodyOffset+cursor,tail);
                ByteBuffer directory=ByteBuffer.wrap(tree).order(ByteOrder.LITTLE_ENDIAN);
                for(Row row:rows) {
                    if(legacy) { int p=directory.position(); directory.put(row.rawPath); directory.position(p+1024); directory.putInt((int)(bodyOffset+row.offset)); }
                    else if(source.format()==PvfFormat.UUID) directory.putInt((int)row.number).putInt(row.rawPath.length).put(row.rawPath).putInt(row.length).putInt(row.key).putInt((int)row.offset);
                    else directory.putInt((int)row.number).putInt((int)row.offset).putInt(row.length).putInt(row.key).putInt(row.rawPath.length).put(row.rawPath);
                }
                directory.put(opaque);
                byte[] encrypted=tree.clone();
                if(!legacy) { int key=PvfIo.crc32(tree,rows.size()); fields.putInt(source.treeKeyPosition(),key); PvfIo.encrypt(encrypted,key^source.keyMask()); }
                PvfIo.writeFully(out,ByteBuffer.wrap(header),0); PvfIo.writeFully(out,ByteBuffer.wrap(encrypted),header.length);
                out.truncate(bodyOffset+bodyLength); out.force(true);
            }
            source.verifyUnchanged();
            try(PvfArchive result=PvfArchive.open(temporary)) {
                if(result.fileCount()!=rows.size() || !Arrays.equals(header,result.header()) || !Arrays.equals(tree,result.plainTree())) throw new IOException("Structural export directory validation failed");
                for(Row row:rows) {
                    PvfEntry actual=result.entry(row.path);
                    if(actual.offset()!=row.offset || actual.length()!=row.length || !Arrays.equals(actual.rawPath(),legacy?Arrays.copyOf(row.rawPath,1024):row.rawPath)) throw new IOException("Structural metadata validation failed: "+row.path);
                    byte[] expected=row.replacement==null?source.readBytes(row.original):row.replacement;
                    if(!Arrays.equals(expected,result.readBytes(actual))) throw new IOException("Structural content validation failed: "+row.path);
                    if(!legacy && !Arrays.equals(padded(source,row),result.readPlainPadded(actual))) throw new IOException("Structural padding validation failed: "+row.path);
                    if(legacy) {
                        byte[] block=block(source,row,bodyOffset,links);
                        if(!Arrays.equals(block,PvfIo.read(result.channel(),bodyOffset+row.offset,24))) throw new IOException("Structural block validation failed: "+row.path);
                    }
                }
                oldCursor=0; cursor=0;
                for(PvfEntry e:source.physicalEntries()) {
                    long gap=e.offset()-oldCursor;
                    if(!PvfIo.equalRange(source.channel(),source.bodyOffset()+oldCursor,result.channel(),bodyOffset+cursor,gap)) throw new IOException("Structural gap validation failed");
                    cursor+=gap; Row row=survivors.get(e.path()); if(row!=null) cursor=row.offset+(legacy?24L+row.length:PvfIo.align4(row.length));
                    oldCursor=e.offset()+e.storedLength();
                }
                for(Row row:rows) if(row.original==null) cursor=row.offset+(legacy?24L+row.length:PvfIo.align4(row.length));
                if(!PvfIo.equalRange(source.channel(),source.bodyOffset()+oldCursor,result.channel(),bodyOffset+cursor,tail)) throw new IOException("Structural trailer validation failed");
            }
            source.verifyUnchanged(); PvfIo.checkCancelled(); Files.move(temporary,target);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static byte[] padded(PvfArchive source,Row row) throws IOException {
        byte[] bytes=row.original!=null && row.length==row.original.length()?source.readPlainPadded(row.original):new byte[(int)PvfIo.align4(row.length)];
        if(row.replacement!=null) System.arraycopy(row.replacement,0,bytes,0,row.length);
        return bytes;
    }
    private static byte[] block(PvfArchive source,Row row,long bodyOffset,Map<Long,Long> links) throws IOException {
        byte[] bytes=row.original==null?new byte[24]:PvfIo.read(source.channel(),source.bodyOffset()+row.original.offset(),24);
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); long self=bodyOffset+row.offset;
        long previous=Integer.toUnsignedLong(b.getInt(20));
        long link=row.original==null?self:links.getOrDefault(previous,self);
        b.putInt(0,1).putInt(4,1).putInt(8,(int)self).putInt(12,row.length).putInt(16,row.length).putInt(20,(int)link);
        return bytes;
    }
    private static void writeRow(PvfArchive source,Row row,FileChannel out,long bodyOffset,boolean legacy,Map<Long,Long> links) throws IOException {
        PvfIo.checkCancelled();
        if(legacy) {
            PvfIo.writeFully(out,ByteBuffer.wrap(block(source,row,bodyOffset,links)),bodyOffset+row.offset);
            if(row.replacement==null) PvfIo.copy(source.channel(),source.bodyOffset()+row.original.offset()+24,out,bodyOffset+row.offset+24,row.length);
            else { byte[] bytes=row.replacement.clone(); PvfIo.legacyCrypt(bytes,true); byte[] check=bytes.clone(); PvfIo.legacyCrypt(check,false);
                if(!Arrays.equals(check,row.replacement)) throw new IOException("Legacy encryption cannot represent content: "+row.path);
                PvfIo.writeFully(out,ByteBuffer.wrap(bytes),bodyOffset+row.offset+24); }
        } else {
            byte[] bytes=padded(source,row); row.key=PvfIo.crc32(bytes,source.format()==PvfFormat.UUID?row.number:row.offset);
            PvfIo.encrypt(bytes,row.key^source.keyMask()); PvfIo.writeFully(out,ByteBuffer.wrap(bytes),bodyOffset+row.offset);
        }
    }
}
