package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Gunner state 8, skills 5/20/47/53: 0x600690 entry, 0x5F9CB0 update, 0x5F37F0 weapon clocks. */
final class Act1GunnerPrecisionCast {
    static final String DOWN="precision:down";
    private static final String ROOT="character/gunner/",EFFECT=ROOT+"effect/animation/";
    private static final String GUN_INFO="etc/gunnerdefaultguninfo.etc";
    record Gun(int type,int reaction,int after,int reload,int draw,String particle) {}

    private Act1GunnerPrecisionCast() {}

    /** Caller validates the skill catalog. A missing weapon uses native subtype 0 (revolver). */
    static Act1SkillSequence find(int skill,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,boolean down,String weaponPath) throws IOException {
        return find(skill,text,lookup,actions,level,down,weaponPath,false);
    }

    static Act1SkillSequence find(int skill,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,boolean down,String weaponPath,boolean wall) throws IOException {
        if(skill!=5&&skill!=20&&skill!=47&&skill!=53)return null;
        boolean reverse=skill==20,headshot=skill==5||skill==20,napalm=skill==53;
        boolean reloadAfter=skill!=47;
        // 0x5F3BB0 returns the downward branch unconditionally for skill 53.
        down=down||napalm;
        String headshotText=text;
        if(reverse){
            if(!"gunner/headshot.skl".equals(GameScript.catalog(lookup.read("skill/gunnerskill.lst")).get(5)))return null;
            headshotText=lookup.read("skill/gunner/headshot.skl");if(headshotText==null)return null;
        }
        int type=0,attackBonus=0;
        List<GameScript.Field> weaponFields=List.of();
        if(weaponPath!=null&&!weaponPath.isEmpty()){
            String weapon=lookup.read(GameScript.normalize(weaponPath));if(weapon==null)return null;
            var fields=GameScript.fields(weapon);
            weaponFields=fields;
            if(!GameScript.flag(GameScript.value(fields,"equipment type")).equals("weapon"))return null;
            type=GameScript.integer(GameScript.value(fields,"sub type"),-1);
            attackBonus=GameScript.integer(GameScript.value(fields,"attack speed"),0);
        }
        var gun=gun(lookup,type,down,weaponFields);if(gun==null)return null;
        String character=lookup.read(ROOT+"gunner.chr");if(character==null)return null;
        int attackSpeed=number(GameScript.value(GameScript.fields(character),"attack speed"))+attackBonus;
        if(attackSpeed<100||attackSpeed>5000)return null;
        String prepare="attack motion:1",shoot="etc motion:"+(down?3:2),reload="etc motion:4";
        if(!bound(actions,prepare,"attack2.ani")||!bound(actions,shoot,down?"attack2shootdown.ani":"attack2shootup.ani")
                ||reloadAfter&&!bound(actions,reload,"reload.ani"))return null;
        var ready=Act1SkillSequence.frames(ROOT+"gunner.chr",actions.get(prepare),lookup);
        var firing=Act1SkillSequence.frames(ROOT+"gunner.chr",actions.get(shoot),lookup);
        if(ready.size()<4||ready.get(ready.size()-1).delay()<10000||firing.size()<3||firing.get(0).delay()<10000)return null;
        int speedBonus=headshot?Act1SkillSequence.levelValue(GameScript.fields(headshotText),reverse?1:level,1,0,0,500):0;
        int drawSpeed=Math.max(1,(int)((int)(gun.draw()*attackSpeed/1000.0)*(1+speedBonus/100.0)));
        int reloadSpeed=Math.max(1,(int)(gun.reload()*attackSpeed/1000.0));
        long readyTime=ready.subList(2,ready.size()-1).stream().mapToLong(ResourceScript.Frame::delay).sum();
        if(readyTime<=0||readyTime>10000)return null;
        long aimed=napalm?Act1SkillSequence.castingTime(GameScript.fields(text),level,400):(long)Math.ceil(readyTime*1000.0/drawSpeed);
        int reaction=(int)((long)gun.reaction()*1000/attackSpeed);
        long fired=aimed+reaction;
        long after=(long)gun.after()*1000/attackSpeed;
        long extra=(long)(skill==47?Act1SkillSequence.staticValue(GameScript.fields(text),0,250):100)*1000/reloadSpeed;
        long recovered=fired+after+extra;
        if(aimed<=0||reaction<=0||recovered<=fired||recovered>30000)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();
        poses.add(new Act1SkillSequence.Pose(prepare,0,aimed,null,true,null,2,ready.size()-1,reverse));
        // The native state jumps past the first held frame at fired; the final frame remains held until recovery.
        poses.add(new Act1SkillSequence.Pose(shoot,aimed,recovered,reaction,false,null,0,Integer.MAX_VALUE,reverse));
        if(reloadAfter){
            var frames=Act1SkillSequence.frames(ROOT+"gunner.chr",actions.get(reload),lookup);
            long duration=Act1SkillSequence.duration(frames,frames.size(),null);
            if(duration<=0||duration>10000)return null;
            long end=recovered+(long)Math.ceil(duration*1000.0/reloadSpeed);
            poses.add(new Act1SkillSequence.Pose(reload,recovered,end,null,true,null,0,Integer.MAX_VALUE,reverse));
            recovered=end;
        }
        String particleText=lookup.read(gun.particle());if(particleText==null)return null;
        var particle=ParticleScript.parse(particleText);
        var visuals=new ArrayList<Act1SkillSequence.Visual>();var emitters=new ArrayList<SkillParticleEmitter>();
        double side=reverse?-1:1;
        // State 8's second attack adds (38,0,-7) or (34,0,-5) in sub_5F3A70.
        double muzzleX=side*(particle.position().x()+(down?34:38));
        double muzzleY=particle.position().y()-particle.position().z()+(down?5:7);
        if(napalm){
            if(!visual(visuals,EFFECT+"napalmbombshoot.ani","地狱烈炎 · 枪口火球",3500,fired,muzzleX,muzzleY,false,lookup))return null;
            return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),"");
        }
        // Native gun PTLs are one instantaneous passive object; custom repeat emitters are not this state recipe.
        if(!particle.passive()||particle.frequency()!=-1||particle.maximum()!=1||particle.objects().size()!=1
                ||particle.life()<=0||particle.life()>10000)return null;
        var bullet=Act1SkillSequence.objectMotions(lookup,particle.objects().get(0).index());
        if(bullet.isEmpty())return null;
        boolean bulletHasContent=ResourceScript.animation(lookup.read(bullet.get(0))).frames().stream()
                .anyMatch(f->!f.image().isEmpty()||!f.combat().attack().isEmpty());
        if(headshot&&!visual(visuals,EFFECT+"headshot"+(down?"down":"up")+".ani","爆头一击 · 枪口光",3501,fired,muzzleX,muzzleY,reverse,lookup))return null;
        String muzzle=muzzle(type,down);
        if(!visual(visuals,muzzle,"枪口火光",3502,fired,muzzleX,muzzleY,reverse,lookup,0,0))return null;
        // Handcannon's passive object is an invisible attack volume, not a visible flying projectile.
        String shotLabel=reverse?"回头一击":skill==47?"浮空弹":"爆头一击";
        if(wall)Act1GunnerUtilityCast.wallBullet(visuals,emitters,lookup,gun,particle,fired,side*(down?78:85),0,
                down?85:112,reverse,shotLabel);
        else if(bulletHasContent)emitters.add(new SkillParticleEmitter(gun.particle(),"精准射击 · 子弹",fired,1,0,side*(down?78:85),0,down?85:112,
                Double.NaN,1,true,1,reverse));
        if(type==1){
            String shell=ROOT+"effect/particle/automaticshell.ptl";if(lookup.read(shell)==null)return null;
            emitters.add(new SkillParticleEmitter(shell,"自动手枪 · 抛壳",fired,1,0,side*(down?101:105),0,down?105:115,
                    Double.NaN,1,true,1,reverse));
        }else if(type==2){
            if(!visual(visuals,EFFECT+"musketdust/musketdustsmall"+(down?2:1)+".ani","步枪 · 枪口烟尘",3503,fired,side*(down?88:87),down?-98:-120,reverse,lookup)
                    ||!visual(visuals,EFFECT+"musketdust/musketdustlarge.ani","步枪 · 膛口烟尘",3504,fired,side*(down?48:47),down?-133:-120,reverse,lookup))return null;
        }
        if(reverse){
            if(!visual(visuals,EFFECT+"turningshoot.ani","回头一击 · 转身光",3500,0,0,0,true,lookup))return null;
            var turn=visuals.remove(visuals.size()-1);
            visuals.add(new Act1SkillSequence.Visual(turn.path(),turn.label(),turn.layer(),0,Math.min(recovered,turn.end()),0,0,null,1,true,turn.style()));
        }
        long finish=recovered;
        for(var v:visuals)finish=Math.max(finish,v.end());
        for(var e:emitters)finish=Math.max(finish,e.start()+Math.min(e.maxDuration(),
                e.emitFor()+SkillParticleEmitter.tail(ParticleScript.parse(lookup.read(e.path())))));
        if(finish>recovered&&actions.containsKey("waiting motion"))poses.add(new Act1SkillSequence.Pose("waiting motion",recovered,finish,null,false,null,0,Integer.MAX_VALUE,reverse));
        String weaponName=switch(type){case 0->"左轮枪";case 1->"自动手枪";case 2->"步枪";case 3->"手炮";default->"手弩";};
        String note=wall?shotLabel+"演示一发左轮弹撞墙，墙面位置为固定演示取样。"
                :(reverse?"回头一击立即转身，关联爆头一击 Lv.1；":skill==47?"浮空弹使用单发弹道，收招后直接结束；":"爆头一击按当前技能等级调整拔枪；")
                +weaponName+"的出弹、收招和装填时序取当前枪械配置，速度使用职业基础值与所选武器；未叠加其他装备、被动和子弹增益，目标受击不在无目标预览中执行";
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.copyOf(emitters),note);
    }

    private static boolean bound(Map<String,String> actions,String key,String name){
        String path=actions.get(key);return path!=null&&GameScript.normalize(path).endsWith("/"+name);
    }

    static String muzzle(int type,boolean down){
        return EFFECT+"shooteffect/"+(down?"down":"up")+switch(type){
            case 0,1->"pistol";
            case 2->"musket";
            case 3->"handcannon";
            default->"bowgun";
        }+".ani";
    }

    static int number(String text){
        try{double value=Double.parseDouble(text);return Double.isFinite(value)&&value>=0&&value<=10000?(int)value:0;}
        catch(NumberFormatException ignored){return 0;}
    }

    private static boolean visual(List<Act1SkillSequence.Visual> visuals,String path,String label,int layer,long start,
                                  double x,double y,boolean reverse,GameScript.Lookup lookup,Integer... variations) throws IOException {
        String text=lookup.read(path);if(text==null)return false;
        var ani=ResourceScript.animation(text);long duration=new AnimationTimeline(ani).duration();
        if(ani.loop()||duration<=0||duration>10000)return false;
        var style=new Act1SkillSequence.VisualStyle(null,0,List.of(),Map.of(),reverse);
        visuals.add(new Act1SkillSequence.Visual(path,label,layer,start,start+duration,x,y,null,1,true,style,List.of(variations)));
        return true;
    }

    /** 0x5F7B20 loads this subtype-indexed table, including edited PTL paths. */
    static Gun gun(GameScript.Lookup lookup,int type,boolean down,List<GameScript.Field> weapon) throws IOException {
        if(type<0||type>4)return null;
        String text=lookup.read(GUN_INFO);if(text==null)return null;
        int current=-1;List<String> integers=List.of(),strings=List.of();
        for(var f:GameScript.fields(text)){
            if(f.tag().equals("sub type"))current=GameScript.integer(f.first(),-1);
            else if(current==type&&f.tag().equals("integer"))integers=f.values();
            else if(current==type&&f.tag().equals("string"))strings=f.values();
        }
        if(integers.size()<10||strings.size()<2)return null;
        // 0x5F4BC0 overlays equipment int/string data; -1 and blank entries inherit defaults.
        integers=new ArrayList<>(integers);strings=new ArrayList<>(strings);
        var intOverrides=GameScript.values(weapon,"int data");
        for(int i=0;i<Math.min(integers.size(),intOverrides.size());i++){
            if(!intOverrides.get(i).equals("-1"))integers.set(i,intOverrides.get(i));
        }
        var stringOverrides=GameScript.values(weapon,"string data");
        for(int i=0;i<Math.min(strings.size(),stringOverrides.size());i++){
            if(!stringOverrides.get(i).isEmpty())strings.set(i,stringOverrides.get(i));
        }
        int reaction=GameScript.integer(integers.get(3),0),after=GameScript.integer(integers.get(4),0);
        int reload=GameScript.integer(integers.get(5),0),draw=GameScript.integer(integers.get(9),0);
        if(reaction<1||reaction>1000||after<0||after>1000||reload<100||reload>5000||draw<100||draw>5000)return null;
        String path=GameScript.resolve(GUN_INFO,strings.get(down?1:0),lookup);
        return path==null?null:new Gun(type,reaction,after,reload,draw,path);
    }
}
