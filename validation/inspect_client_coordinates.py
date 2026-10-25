"""Run through the existing IDA launcher against a copied client IDB; outputs stay in build/."""
import re
import idc
import idaapi
import ida_auto
import ida_bytes
import ida_funcs
import ida_hexrays
import ida_name
import idautils
import ida_segment
import struct

ida_auto.auto_wait()
ida_hexrays.init_hexrays_plugin()
args = idc.ARGV
args = args[:3] + [value for value in args[3:] if not value.endswith('.py')]
with open(args[1], "w", encoding="utf-8") as out:
    out.write("database=" + idaapi.get_path(idaapi.PATH_TYPE_IDB) + "\n")
    out.write("arguments=" + repr(args) + "\n")

    def references(ea):
        found = set()
        for ref in idautils.XrefsTo(ea):
            fn = ida_funcs.get_func(ref.frm)
            out.write("  xref %08X %s\n" % (ref.frm, ida_funcs.get_func_name(fn.start_ea) if fn else "data"))
            if fn:
                found.add(fn.start_ea)
            else:
                for parent in list(idautils.XrefsTo(ref.frm))[:12]:
                    pf = ida_funcs.get_func(parent.frm)
                    out.write("    parent %08X %s\n" % (parent.frm, ida_funcs.get_func_name(pf.start_ea) if pf else "data"))
                    if pf:
                        found.add(pf.start_ea)
        return found

    def decompile(ea):
        fn = ida_funcs.get_func(ea)
        if not fn:
            out.write("NO_FUNCTION %08X\n" % ea)
            return
        out.write("\nFUNCTION %08X %s\n" % (fn.start_ea, ida_funcs.get_func_name(fn.start_ea)))
        try:
            out.write(str(ida_hexrays.decompile(fn.start_ea)) + "\n")
        except Exception as error:
            out.write("DECOMPILE_ERROR " + str(error) + "\n")

    mode = args[2]
    if mode == "fields":
        start, end = int(args[3], 16), int(args[4], 16)
        targets = {int(v, 16) for v in args[5:]}; found = set()
        for ea in idautils.Heads(start, end):
            for i in range(3):
                if idc.get_operand_type(ea, i) == idc.o_displ and idc.get_operand_value(ea, i) in targets:
                    fn = ida_funcs.get_func(ea)
                    out.write('%08X %s\n' % (ea, idc.generate_disasm_line(ea, 0)))
                    if fn: found.add(fn.start_ea)
        for ea in sorted(found): decompile(ea)
    elif mode == "virtual-attachments":
        import json
        from pathlib import Path
        folder=Path(args[1]).parent
        graph=json.loads((folder/'constructor-graph.json').read_text(encoding='utf8')); records=graph['records']
        asset_functions={int(v,16) for v in re.findall(r'xref [0-9A-F]+ sub_([0-9A-F]+)',(folder/'native-equipment-assets.txt').read_text(encoding='utf8'))}
        def sources(ea,seen=None):
            if seen is None: seen=set()
            if ea in seen or ea == 0x659770: return ''
            seen.add(ea); record=records.get(str(ea),{})
            return record.get('source','')+'\n'+''.join(sources(c,seen) for c in record.get('calls',[]) if str(c) in records)
        for line in (folder/'monster-id-constructors.txt').read_text(encoding='utf-8-sig').splitlines():
            identity,constructor=line.split(); source=records.get(str(int(constructor,16)),{}).get('source','')
            tables=re.findall(r'\*this\s*=\s*&([^;]+)|\*\(_DWORD \*\)this\s*=\s*&([^;]+)',source)
            if not tables: continue
            name=''.join(tables[-1]); addr=ida_name.get_name_ea(idaapi.BADADDR,name)
            if addr==idaapi.BADADDR and '::' in name: addr=ida_name.get_name_ea(idaapi.BADADDR,'??_7'+name.split('::')[0]+'@@6B@')
            if addr==idaapi.BADADDR: continue
            reachable=sources(int(constructor,16))
            offsets={int(v) for v in re.findall(r'\(\*this \+ (\d+)\)|\(\*\(_DWORD \*\)this \+ (\d+)\)',reachable) for v in v if v}
            for offset in sorted(offsets):
                target=ida_bytes.get_dword(addr+offset)
                if target in asset_functions:
                    out.write('MONSTER %s VTABLE %X SLOT %d FUNCTION %X\n' % (identity,addr,offset,target)); decompile(target)
    elif mode == "constructor-graph":
        import json
        from pathlib import Path
        evidence = Path(args[1]).parent / 'native-equipment-assets.txt'
        asset_functions = {int(v,16) for v in re.findall(r'xref [0-9A-F]+ sub_([0-9A-F]+)', evidence.read_text(encoding='utf8'))}
        factory = str(ida_hexrays.decompile(0x666DA0))
        roots = {int(v,16) for v in re.findall(r'sub_([0-9A-F]+)\(',factory)}
        pending = [ea for ea in roots if 0x659000 <= ea < 0x710000] + list(asset_functions)
        records = {}
        asset_names = {}
        for ea,name in idautils.Names():
            if name.startswith('aMonster'):
                value = idc.get_strlit_contents(ea)
                if value and value.lower().endswith(b'.equ'): asset_names[name]=value.decode('ascii')
        while pending:
            ea = pending.pop()
            if ea in records or ea == 0x659770: continue
            if not ida_funcs.get_func(ea): idc.create_insn(ea); ida_funcs.add_func(ea); ida_auto.auto_wait()
            try: source = str(ida_hexrays.decompile(ea))
            except Exception as error: source = 'ERROR '+str(error)
            calls = [int(v,16) for v in re.findall(r'sub_([0-9A-F]+)\(',source)]
            calls = [v for v in calls if v != ea]
            records[ea] = {'source':source,'calls':calls}
            pending.extend(v for v in calls if (0x659000 <= v < 0x667000 or v in roots) and v not in records)
            if re.search(r'\*this\s*=\s*&|\*\(_DWORD \*\)this\s*=\s*&',source):
                pending.extend(v for v in calls if 0x650000 <= v < 0x710000 and v not in records)
        Path(args[1]).with_suffix('.json').write_text(json.dumps({'factory':factory,'records':records,'assetNames':asset_names},indent=2),encoding='utf8')
        out.write('functions='+str(len(records))+'\n')
    elif mode == "raw-callers":
        targets = {int(v,16) for v in args[3:]}
        for segbase in idautils.Segments():
            seg = ida_segment.getseg(segbase)
            data = ida_bytes.get_bytes(seg.start_ea, seg.end_ea-seg.start_ea, ida_bytes.GMB_READALL)
            out.write('SEG %08X-%08X len=%s\n' % (seg.start_ea,seg.end_ea,len(data) if data else None))
            if data is None: continue
            offset = data.find(b'\xe8')
            while 0 <= offset < len(data)-4:
                target = segbase+offset+5+struct.unpack_from('<i',data,offset+1)[0]
                if target in targets:
                    fn=ida_funcs.get_func(segbase+offset)
                    out.write('%08X CALL %08X owner %s bytes %s\n' % (segbase+offset,target,hex(fn.start_ea) if fn else '?',data[max(0,offset-24):offset+5].hex()))
                offset=data.find(b'\xe8',offset+1)
    elif mode == "equipment-assets":
        for ea,name in idautils.Names():
            if not name.startswith('aMonster'): continue
            value = idc.get_strlit_contents(ea)
            if value and value.lower().endswith(b'.equ'):
                out.write('%08X %s\n' % (ea,value.decode('ascii','replace'))); references(ea)
    elif mode == "names":
        pattern = re.compile(args[3],re.I)
        for ea,name in idautils.Names():
            if pattern.search(name):
                out.write('%08X %s %s\n' % (ea,name,idc.get_strlit_contents(ea)))
    elif mode == "pointers":
        for value in args[3:]:
            target = int(value, 16); needle = struct.pack('<I', target)
            out.write('POINTERS %08X\n' % target)
            for segbase in idautils.Segments():
                seg = ida_segment.getseg(segbase); data = ida_bytes.get_bytes(seg.start_ea, seg.end_ea-seg.start_ea, ida_bytes.GMB_READALL)
                if data is None: continue
                offset = data.find(needle)
                while offset >= 0:
                    out.write('%08X %s\n' % (segbase+offset,ida_name.get_name(segbase+offset))); references(segbase+offset)
                    offset = data.find(needle, offset+1)
    elif mode == "rtti":
        for value in args[3:]:
            ea = int(value, 16)
            for item in idautils.XrefsTo(ea):
                ref = item.frm
                col = ref - 12
                if ida_bytes.get_dword(col) == 0 and ida_bytes.get_dword(col + 4) < 1024 and ida_bytes.get_dword(col + 8) == 0:
                    for vtitem in idautils.XrefsTo(col):
                        vtref = vtitem.frm
                        vt = vtref + 4
                        out.write("RTTI %08X COL %08X VTABLE %08X\n" % (ea,col,vt)); references(vt)
                        for off in range(0, 0x400, 4):
                            target = ida_bytes.get_dword(vt + off)
                            out.write("+%03X %08X %s\n" % (off,target,ida_name.get_name(target)))
    elif mode == "constants":
        targets = {int(v, 16) for v in args[3:]}
        for funcea in idautils.Functions():
            hits = []
            for ea in idautils.FuncItems(funcea):
                for i in range(3):
                    if idc.get_operand_type(ea, i) == idc.o_imm and idc.get_operand_value(ea, i) in targets:
                        hits.append((ea, idc.get_operand_value(ea, i)))
            if hits:
                out.write("%08X %s %s\n" % (funcea, ida_funcs.get_func_name(funcea), hits))
    elif mode == "strings":
        pattern = re.compile(args[3], re.I)
        for string in idautils.Strings():
            if pattern.search(str(string)):
                out.write("STRING %08X %s\n" % (string.ea, string)); references(string.ea)
    elif mode == "values":
        import struct
        for value in args[3:]:
            ea = int(value, 16)
            out.write("%08X dword=%08X float=%s double=%s\n" % (ea, ida_bytes.get_dword(ea), struct.unpack('<f', ida_bytes.get_bytes(ea, 4))[0], struct.unpack('<d', ida_bytes.get_bytes(ea, 8))[0]))
    elif mode == "discover":
        targets = {"[tile]", "[extended tile]", "[animation]", "[image pos]", "[image]", "[pass type]", "[background animation]", "[monster]", "[floating height]"}
        for string in idautils.Strings():
            value = str(string)
            if value.lower() in targets or value.startswith(".?AV") and re.search(r"Map|Tile|Animation|Image|DrawObject", value, re.I):
                out.write("STRING %08X %s\n" % (string.ea, value)); references(string.ea)
        for ea in [0x8D396D, 0x4E4285, 0x82C216, 0x82E66A]:
            out.write("CALLERS %08X\n" % ea); references(ea)
    elif mode in ("dec", "bundle", "force"):
        for value in args[3:]:
            ea = int(value, 16)
            if mode == "force" and not ida_funcs.get_func(ea):
                idc.create_insn(ea); ida_funcs.add_func(ea); ida_auto.auto_wait()
            decompile(ea)
            if mode in ("bundle", "force"): references(ea)
    elif mode == "refs":
        for value in args[3:]:
            ea = int(value, 16); out.write("REFERENCES %08X %s\n" % (ea, ida_name.get_name(ea))); references(ea)
    elif mode == "table":
        for value in args[3:]:
            ea = int(value, 16)
            for offset in range(0, 0x240, 4):
                target = ida_bytes.get_dword(ea + offset)
                out.write("%08X +%03X = %08X %s\n" % (ea, offset, target, ida_name.get_name(target)))
    elif mode == "dis":
        ea = int(args[3], 16); end = int(args[4], 16)
        while ea < end:
            out.write("%08X %s\n" % (ea, idc.generate_disasm_line(ea, 0)))
            ea = idc.next_head(ea, end)
    out.flush()
idc.qexit(0)
