package studio.pvf.preview;

/** World anchor and clock for an ACT1 particle attachment. */
record SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                            double x,double y,double z,double clipX,double scale,boolean overridePosition,double movementScale,
                            boolean reverseFacing,SkillMotion sourceMotion,String animationOverride,long maxDuration,
                            boolean decorative) {
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                         double x,double y,double z,double clipX,double scale,boolean overridePosition,double movementScale,
                         boolean reverseFacing,SkillMotion sourceMotion,String animationOverride,long maxDuration){
        this(path,label,start,emitFor,frequency,x,y,z,clipX,scale,overridePosition,movementScale,reverseFacing,
                sourceMotion,animationOverride,maxDuration,false);
    }
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                         double x,double y,double z,double clipX,double scale,boolean overridePosition,double movementScale,
                         boolean reverseFacing,SkillMotion sourceMotion,String animationOverride){
        this(path,label,start,emitFor,frequency,x,y,z,clipX,scale,overridePosition,movementScale,reverseFacing,
                sourceMotion,animationOverride,Long.MAX_VALUE);
    }
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                         double x,double y,double z,double clipX,double scale,boolean overridePosition,double movementScale,
                         boolean reverseFacing,SkillMotion sourceMotion){
        this(path,label,start,emitFor,frequency,x,y,z,clipX,scale,overridePosition,movementScale,reverseFacing,sourceMotion,"");
    }
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                         double x,double y,double z,double clipX,double scale,boolean overridePosition,double movementScale,
                         boolean reverseFacing){
        this(path,label,start,emitFor,frequency,x,y,z,clipX,scale,overridePosition,movementScale,reverseFacing,null);
    }
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                         double x,double y,double z,double clipX,double scale,boolean overridePosition,double movementScale){
        this(path,label,start,emitFor,frequency,x,y,z,clipX,scale,overridePosition,movementScale,false);
    }
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                         double x,double y,double z,double clipX,double scale,boolean overridePosition){
        this(path,label,start,emitFor,frequency,x,y,z,clipX,scale,overridePosition,scale);
    }
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,
                         double x,double y,double z,double clipX,double scale){
        this(path,label,start,emitFor,frequency,x,y,z,clipX,scale,false);
    }
    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,double x,double z,double clipX) {
        this(path,label,start,emitFor,frequency,x,0,z,clipX,1);
    }
    static long tail(ParticleScript script){
        return script.life()==0?800:Math.min(7000,(long)Math.ceil(script.life()*(1+script.random()/200)));
    }

    SkillParticleEmitter withoutAttack(){
        return new SkillParticleEmitter(path,label,start,emitFor,frequency,x,y,z,clipX,scale,overridePosition,
                movementScale,reverseFacing,sourceMotion,animationOverride,maxDuration,true);
    }

    SkillParticleEmitter(String path,String label,long start,long emitFor,double frequency,double x,double z) {
        this(path,label,start,emitFor,frequency,x,z,Double.NaN);
    }
}
