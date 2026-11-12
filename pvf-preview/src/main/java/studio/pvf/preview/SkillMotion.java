package studio.pvf.preview;

/** World-space travel sampled against the cast clock. */
record SkillMotion(long start,long travelTime,double velocity,double acceleration,
                   double verticalVelocity,double verticalAcceleration,double height,Orbit orbit,double initialX) {
    private static final double GROUND_PROJECTION=.29;

    SkillMotion(long start,long travelTime,double velocity,double acceleration,double verticalVelocity,
                double verticalAcceleration,double height){
        this(start,travelTime,velocity,acceleration,verticalVelocity,verticalAcceleration,height,null);
    }
    SkillMotion(long start,long travelTime,double velocity,double acceleration,double verticalVelocity,
                double verticalAcceleration,double height,Orbit orbit){
        this(start,travelTime,velocity,acceleration,verticalVelocity,verticalAcceleration,height,orbit,0);
    }
    SkillMotion(long start,long travelTime,double velocity,double acceleration){
        this(start,travelTime,velocity,acceleration,0,0);
    }
    SkillMotion(long start,long travelTime,double velocity,double acceleration,double initialX){
        this(start,travelTime,velocity,acceleration,0,0,0,null,initialX);
    }

    SkillMotion(long start,long travelTime,double velocity,double acceleration,double verticalVelocity,double verticalAcceleration){
        this(start,travelTime,velocity,acceleration,verticalVelocity,verticalAcceleration,0);
    }

    double offsetAt(long time){
        if(orbit!=null)return initialX+orbit.offset(elapsed(time),false);
        double seconds=seconds(time);
        return initialX+seconds*(velocity+.5*acceleration*seconds);
    }

    double heightAt(long time){
        double seconds=seconds(time);
        return Math.max(0,height+seconds*(verticalVelocity+.5*verticalAcceleration*seconds));
    }

    double offsetYAt(long time){
        if(orbit==null)return -heightAt(time);
        long age=elapsed(time);
        return orbit.offset(age,true)*GROUND_PROJECTION-orbit.elevation(age);
    }

    double maximumHeight(long from,long to){
        if(orbit!=null)return orbitDepth();
        double high=Math.max(heightAt(from),heightAt(to));
        if(verticalAcceleration>=0)return high;
        long apex=start+Math.round(-1000*verticalVelocity/verticalAcceleration);
        return apex>from&&apex<to?Math.max(high,heightAt(apex)):high;
    }

    private double seconds(long time){
        return elapsed(time)/1000.0;
    }

    private long elapsed(long time){return Math.max(0,Math.min(travelTime,time-start));}

    static SkillMotion orbit(long start,long duration,Orbit orbit){
        return new SkillMotion(start,duration,0,0,0,0,0,orbit);
    }

    double orbitRadius(){return orbit==null?0:orbit.radius();}
    double orbitDepth(){return orbit==null?0:orbitRadius()*GROUND_PROJECTION+orbit.elevationRange();}
    boolean sortsByGroundDepth(){return orbit!=null&&orbit.sortByGroundDepth();}
    double groundDepthAt(long time){return orbit==null?0:orbit.offset(elapsed(time),true);}

    record Orbit(ParticleScript.Axis x,ParticleScript.Axis y,double term,String changing,double degrees,
                 ParticleScript.Axis z,boolean sortByGroundDepth){
        Orbit(ParticleScript.Axis x,ParticleScript.Axis y,double term,String changing,double degrees,ParticleScript.Axis z){
            this(x,y,term,changing,degrees,z,false);
        }
        Orbit(ParticleScript.Axis x,ParticleScript.Axis y,double term,String changing,double degrees){
            this(x,y,term,changing,degrees,null);
        }

        double offset(long age,boolean depth){
            double dx=ParticleTimeline.displacement(x,term,changing,age);
            double dy=ParticleTimeline.displacement(y,term,changing,age);
            double angle=Math.toRadians(degrees);
            return depth?dx*Math.sin(angle)+dy*Math.cos(angle):dx*Math.cos(angle)-dy*Math.sin(angle);
        }

        double radius(){
            return Math.hypot(Math.max(Math.abs(x.start()),Math.abs(x.end())),
                    Math.max(Math.abs(y.start()),Math.abs(y.end())));
        }

        double elevation(long age){return z==null?0:ParticleTimeline.displacement(z,term,changing,age);}

        double elevationRange(){return z==null?0:Math.max(Math.abs(z.start()),Math.abs(z.end()));}
    }
}
