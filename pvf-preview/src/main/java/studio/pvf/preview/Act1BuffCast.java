package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.SpriteOverride;
import studio.pvf.preview.Act1SkillSequence.Visual;

/** ACT1 state 14 self-buffs and state 10 priest casts, using the caster as the preview recipient. */
final class Act1BuffCast {
    private static final String COMMON = "common/commoneffect/animation/";
    private static final String PARTICLES = "common/commoneffect/particle/";
    private static final String PRIEST = "character/priest/effect/animation/";
    private static final String MAGE = "character/mage/effect/animation/";
    private static final String BUFF_ICON = "common/commoneffect/bufficon.img";
    private static final String BUFF = "buff motion";
    private static final String WAIT = "waiting motion";
    static final String SHIELD_HIT = "mage-aurashield:hit";
    private static final String SHIELD_PATH = "skill/mage/aurashield.skl";
    // Priest status casts serialize mode 2 in 0x622CB0. The shared handler indexes the four CHR groups from zero.
    private static final String PREPARE = "throw motion 3-1";
    private static final String RELEASE = "throw motion 3-2";
    private static final int ABOVE_HEAD = 30;
    private static final long PERSISTENT_PREVIEW_MS = 1800;
    private static final int BACK = 499;
    private static final int FRONT = 3501;
    private static final int ABOVE = 3700;

    private enum Buff {
        LEAP(175, 4, "跃翔"), MEMORY(176, 5, "远古记忆"), WILL(180, 21, "不屈意志"),
        SHIELD(37, 32, "魔法盾"), POWER_FIST(37, 6, "强拳"), OVERDRIVE(38, 7, "破极兵刃"),
        RECKLESS(40, 12, "暴走"), STRIKING(7, 41, "武器祝福"), SLOW_HEAL(51, -1, "缓慢愈合"),
        FAST_HEAL(55, -1, "快速愈合"), BLESS(53, -1, "天使祝福"), DIVINE_LIGHT(22, 44, "圣光守护"),
        CHAKRA_PASSION(47, 47, "脉轮 · 烈焰"), CHAKRA_CALMNESS(48, 46, "脉轮 · 寒冰");

        final int skillId, icon;
        final String label;

        Buff(int skillId, int icon, String label) {
            this.skillId = skillId;
            this.icon = icon;
            this.label = label;
        }
    }

    private Act1BuffCast() {}

    @FunctionalInterface
    interface ActorHeight {
        int read(String job) throws IOException;
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                 Map<String,String> actions, int level) throws IOException {
        return find(path, text, lookup, actions, level, Act1BuffCast::stockHeight);
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                 Map<String,String> actions, int level, ActorHeight actorHeight) throws IOException {
        return find(path, text, lookup, actions, level, actorHeight, false);
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                 Map<String,String> actions, int level, ActorHeight actorHeight,
                                 boolean shieldHit) throws IOException {
        if (shieldHit && !path.equals(SHIELD_PATH)) return null;
        String[] parts = path.split("/", -1);
        if (parts.length != 3 || !parts[0].equals("skill") || !CharacterAppearance.JOBS.contains(parts[1])) return null;
        String job = parts[1];
        // 0x49A9F0 handles the same three skill IDs for all five jobs.
        var buff = switch (parts[2]) {
            case "jumppowerup.skl" -> Buff.LEAP;
            case "magicalattackup.skl" -> Buff.MEMORY;
            case "superarmoroncast.skl" -> Buff.WILL;
            default -> switch (path) {
                case SHIELD_PATH -> Buff.SHIELD;
                case "skill/fighter/slowdownpowerup.skl" -> Buff.POWER_FIST;
                case "skill/swordman/overdrive.skl" -> Buff.OVERDRIVE;
                case "skill/swordman/reckless.skl" -> Buff.RECKLESS;
                case "skill/priest/striking.skl" -> Buff.STRIKING;
                case "skill/priest/slowheal.skl" -> Buff.SLOW_HEAL;
                case "skill/priest/fastheal.skl" -> Buff.FAST_HEAL;
                case "skill/priest/bless.skl" -> Buff.BLESS;
                case "skill/priest/divinelight.skl" -> Buff.DIVINE_LIGHT;
                case "skill/priest/chakraofpassion.skl" -> Buff.CHAKRA_PASSION;
                case "skill/priest/chakraofcalmness.skl" -> Buff.CHAKRA_CALMNESS;
                default -> null;
            };
        };
        if (buff == null || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/" + job + "skill.lst")).get(buff.skillId))) return null;
        var fields = GameScript.fields(text);
        String type = GameScript.flag(GameScript.value(fields, "type"));
        if (!type.isEmpty() && !type.equals("active")) return null;
        long cast = castingTime(fields, level);
        int height = actorHeight.read(job);
        return switch (buff) {
            case STRIKING, SLOW_HEAL, FAST_HEAL, BLESS -> priestCast(buff, height, cast, fields, level, lookup, actions);
            default -> selfBuff(buff, height, cast, fields, level, lookup, actions, shieldHit);
        };
    }

    static GameEffectRecipes.Effect shieldHitChoice(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || !path.equals(SHIELD_PATH) || !Act1SkillSequence.matches(path, "mage", 37, lookup)
                || lookup.read(MAGE + "aurashieldhit1.ani") == null || lookup.read(MAGE + "aurashieldhit2.ani") == null) return null;
        return new GameEffectRecipes.Effect(SHIELD_HIT, "魔法盾 · 受击", "", path, "演示护盾承受哥布林攻击时的双层光效");
    }

    private static Act1SkillSequence selfBuff(Buff buff, int height, long cast, List<GameScript.Field> fields,
                                             int level, GameScript.Lookup lookup,
                                             Map<String,String> actions, boolean shieldHit) throws IOException {
        if (!actions.containsKey(BUFF)) return null;
        var visuals = new ArrayList<Visual>();
        var particles = new ArrayList<SkillParticleEmitter>();
        if (cast > 0) {
            String charge = COMMON + "buffchargeglow.ani";
            // State 14 sets glow y = -height - 30 (0x4A1559) and updates the PTL at z + height + 30 (0x49FB72).
            int z = height + ABOVE_HEAD;
            if (lookup.read(charge) != null) visuals.add(new Visual(charge, buff.label + " · 聚气", FRONT, 0, cast, 0, -z));
            addParticle(particles, lookup, PARTICLES + "buffchargeparticle.ptl", buff.label + " · 聚气粒子", 0, cast, z);
        }
        long end = activate(buff, cast, height, lookup, visuals, particles);
        String note = "预览施法动作及生效图标和光效";
        long activeDuration = levelDuration(fields, level);
        long persistent = Math.min(PERSISTENT_PREVIEW_MS, activeDuration);
        switch (buff) {
            case SHIELD -> {
                // 0x544B70 loads the five shield clips. 0x544A90 switches Start to Loop on completion.
                long opened = addClip(visuals, lookup, MAGE + "aurashieldstart.ani", "魔法盾 · 展开", FRONT, cast, 0);
                String loop = MAGE + "aurashieldloop.ani";
                if (opened == cast || lookup.read(loop) == null) return null;
                end = Math.max(end, opened + PERSISTENT_PREVIEW_MS);
                if (shieldHit) {
                    if (!shieldImpact(lookup, visuals, loop, opened, end)) return null;
                    note = "魔法盾承受哥布林的一次普通攻击，按原版受击状态播放两层光效，随后恢复护盾。";
                } else {
                    visuals.add(new Visual(loop, "魔法盾 · 维持", FRONT, opened, end, 0, 0));
                    note = "魔法盾展开后截取一段持续护盾动画，可切换受击效果。";
                }
            }
            case MEMORY, RECKLESS -> {
                // Status type 1 (0x52B810) and CNReckless (0x53B860) attach the same PTL at different heights.
                int particleZ = buff == Buff.MEMORY ? height / 2 : 50;
                end = Math.max(end, addParticle(particles, lookup, PARTICLES + "attackpowerup.ptl",
                        buff.label + " · 持续粒子", cast, persistent, particleZ));
                note = buff.label + "预览生效效果和一段持续粒子";
            }
            case OVERDRIVE -> {
                // Change-status type 25 asks for this particle in 0x52C030. Some archives lack it.
                long effectEnd = addParticle(particles, lookup, PARTICLES + "overdrive_part.ptl",
                        "破极兵刃 · 持续粒子", cast, persistent, height / 2);
                end = Math.max(end, effectEnd);
                note = effectEnd == cast ? "破极兵刃预览施法与生效效果；当前 PVF 缺少持续粒子脚本"
                        : "破极兵刃预览生效效果和一段持续粒子";
            }
            case DIVINE_LIGHT -> {
                // 0x549300 switches both sides to Stay when the first Appear clip finishes.
                String folder = PRIEST + "divinelight/";
                long appearDuration = duration(folder + "appear1.ani", lookup);
                if (appearDuration == 0) return null;
                long opened = cast + Math.min(activeDuration, appearDuration);
                addLayer(visuals, lookup, folder + "appear1.ani", "圣光守护 · 展开后层", BACK, cast, opened);
                addLayer(visuals, lookup, folder + "appear2.ani", "圣光守护 · 展开前层", FRONT, cast, opened);
                long finish = Math.min(cast + activeDuration, opened + persistent);
                addLayer(visuals, lookup, folder + "stay1.ani", "圣光守护 · 维持后层", BACK, opened, finish);
                addLayer(visuals, lookup, folder + "stay2.ani", "圣光守护 · 维持前层", FRONT, opened, finish);
                addLayer(visuals, lookup, folder + "stay3.ani", "圣光守护 · 维持前层", FRONT + 1, opened, finish);
                end = Math.max(end, finish);
                note = "圣光守护展示护盾展开和一段维持动画，受击和消失由护盾状态触发";
            }
            case CHAKRA_PASSION, CHAKRA_CALMNESS -> {
                String folder = PRIEST + (buff == Buff.CHAKRA_PASSION ? "chakraofpassion/" : "chakraofcalmness/");
                if (cast > 0) addClip(visuals, lookup, folder + "cast.ani", buff.label + " · 施法", FRONT + 1, 0, 0);
                // 0x52C030 starts all six ANI clocks together. 0x52B1E0 draws them around the grounded actor.
                long finish = cast + persistent;
                for (int index = 1; index <= 3; index++) {
                    addLayer(visuals, lookup, folder + "back" + index + ".ani", buff.label + " · 脉轮后层", BACK - 1 + index, cast, finish);
                    addLayer(visuals, lookup, folder + "front" + index + ".ani", buff.label + " · 脉轮前层", FRONT - 1 + index, cast, finish);
                }
                end = Math.max(end, finish);
                note = buff.label + "展示施法与角色站立时的一段脉轮效果";
            }
            default -> { }
        }
        if (end == cast) return null;
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose(BUFF, 0, cast, null, true, null));
        if (actions.containsKey(WAIT)) poses.add(new Pose(WAIT, cast, end));
        if (poses.isEmpty()) poses.add(new Pose(BUFF, 0, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(particles), note);
    }

    /** 0x544A10 resets both hit animations and 0x544A90 ends state 3 when the first layer finishes. */
    private static boolean shieldImpact(GameScript.Lookup lookup, List<Visual> visuals, String loop,
                                        long opened, long end) throws IOException {
        String hit1 = MAGE + "aurashieldhit1.ani";
        String hit2 = MAGE + "aurashieldhit2.ani";
        long hitLength = duration(hit1, lookup);
        String attackPath = Act1SwordAsuraSupplement.GOBLIN + "attack1.ani";
        String attack = lookup.read(attackPath);
        if (hitLength == 0 || duration(hit2, lookup) == 0 || attack == null) return false;
        var frames = ResourceScript.animation(attack).frames();
        if (frames.size() < 4) return false;
        long attackAt = opened + 250;
        long contact = attackAt + Act1SkillSequence.duration(frames, 3, null);
        long attackEnd = attackAt + Act1SkillSequence.duration(frames, frames.size(), null);
        long recovered = contact + hitLength;
        if (recovered >= end) return false;
        visuals.add(new Visual(loop, "魔法盾 · 维持", FRONT, opened, contact, 0, 0));
        visuals.add(new Visual(hit1, "魔法盾 · 受击护盾", FRONT, contact, recovered, 0, 0));
        // Sample one offset within the client's X[-30,29] and Y[-20,19] ranges.
        visuals.add(new Visual(hit2, "魔法盾 · 受击闪光", FRONT + 1, contact, recovered, 12, -8));
        visuals.add(new Visual(loop, "魔法盾 · 恢复维持", FRONT, recovered, end, 0, 0));
        double x = 42;
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "魔法盾 · 演示攻击者", 0, attackAt, x, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "attack1", "魔法盾 · 哥布林攻击", attackAt, attackEnd, x, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "魔法盾 · 演示攻击者", attackEnd, end, x, null);
        return true;
    }

    /** 0x627770 replaces the preparation attachment on release and applies effects to the selected target. */
    private static Act1SkillSequence priestCast(Buff buff, int height, long cast, List<GameScript.Field> fields, int level,
                                               GameScript.Lookup lookup, Map<String,String> actions) throws IOException {
        if (!actions.containsKey(PREPARE) || !actions.containsKey(RELEASE)) return null;
        String prefix = switch (buff) {
            case STRIKING -> "strikingcast";
            case BLESS -> "blesscast";
            default -> "slowhealcast";
        };
        String preparation = PRIEST + prefix + "1.ani";
        String release = PRIEST + prefix + "2.ani";
        if (lookup.read(preparation) == null || lookup.read(release) == null) return null;
        long recover = cast + Math.max(1, Math.min(10000, number(GameScript.values(fields, "static data"), 1, 500)));
        var visuals = new ArrayList<Visual>();
        var particles = new ArrayList<SkillParticleEmitter>();
        if (cast > 0) visuals.add(new Visual(preparation, buff.label + " · 施法", FRONT, 0, cast, 0, 0));
        long end = Math.max(recover, addClip(visuals, lookup, release, buff.label + " · 生效动作", FRONT, cast, 0));
        String note = buff.label + "以施法者为预览目标";
        switch (buff) {
            case SLOW_HEAL, FAST_HEAL -> {
                end = Math.max(end, addClip(visuals, lookup, PRIEST + "slowhealcastwave.ani", buff.label + " · 施放波纹", BACK, cast, 0));
                if (buff == Buff.SLOW_HEAL) {
                    long expires = cast + Math.min(PERSISTENT_PREVIEW_MS, levelDuration(fields, level));
                    visuals.add(new Visual(COMMON + "priestslowhealb.ani", "缓慢愈合 · 持续后层", BACK, cast, expires, 0, -height / 2));
                    visuals.add(new Visual(COMMON + "priestslowhealf.ani", "缓慢愈合 · 持续前层", FRONT + 1, cast, expires, 0, -height / 2));
                    end = Math.max(end, expires);
                    note += "，截取一段持续恢复动画";
                } else {
                    // 0x528A70 draws FastHeal at the target's feet minus 60% of its height, facing right.
                    String effect=COMMON+"fastheal.ani";
                    long effectEnd=cast+duration(effect,lookup);
                    if(effectEnd>cast){
                        visuals.add(new Visual(effect,"快速愈合 · 目标效果",FRONT+1,cast,effectEnd,0,
                                (int)(-.6f*height),null,1,false));
                        end=Math.max(end,effectEnd);
                    }
                }
            }
            case STRIKING -> {
                end = Math.max(end, activate(buff, cast, height, lookup, visuals, particles));
                end = Math.max(end, addClip(visuals, lookup, PRIEST + "strikingbuff.ani", "武器祝福 · 目标效果", FRONT + 1, cast, -height));
            }
            case BLESS -> {
                end = Math.max(end, addClip(visuals, lookup, COMMON + "priestbless.ani", "天使祝福 · 目标效果", FRONT + 1, cast, -height));
                long emission = Math.min(PERSISTENT_PREVIEW_MS, levelDuration(fields, level));
                // CNChangeStatus type 17 spawns the first particle at z + 50 and the second at the ground anchor.
                String prefixPath = "character/priest/effect/particle/blessparticle";
                end = Math.max(end, addParticle(particles, lookup, prefixPath + "1.ptl", "天使祝福 · 持续粒子", cast, emission, 50));
                end = Math.max(end, addParticle(particles, lookup, prefixPath + "2.ptl", "天使祝福 · 持续粒子", cast, emission, 0));
                note += "，截取一段持续粒子";
            }
            default -> throw new AssertionError(buff);
        }
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose(PREPARE, 0, cast));
        poses.add(new Pose(RELEASE, cast, recover));
        if (end > recover && actions.containsKey(WAIT)) poses.add(new Pose(WAIT, recover, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(particles), note);
    }

    private static long activate(Buff buff, long start, int height, GameScript.Lookup lookup,
                                 List<Visual> visuals, List<SkillParticleEmitter> particles) throws IOException {
        return activate(buff.label, buff.icon, start, height, lookup, visuals, particles);
    }

    /** 0x505C20 installs the selected BuffIcon sprite in every blank BuffStartIcon frame. */
    static long activate(String label, int iconIndex, long start, int height, GameScript.Lookup lookup,
                         List<Visual> visuals, List<SkillParticleEmitter> particles) throws IOException {
        int z = height + ABOVE_HEAD;
        String icon = COMMON + "buffstarticon.ani";
        long end = start + duration(icon, lookup);
        if (end > start) visuals.add(new Visual(icon, label + " · 生效图标", ABOVE + 2, start, end, 0, -z,
                null, 1, false, new SpriteOverride(BUFF_ICON, iconIndex)));
        end = Math.max(end, addClip(visuals, lookup, COMMON + "buffstarticonglow.ani", label + " · 图标光晕", ABOVE + 1, start, -z));
        end = Math.max(end, addClip(visuals, lookup, COMMON + "buffstartglow.ani", label + " · 生效光效", ABOVE, start, -z));
        return Math.max(end, addParticle(particles, lookup, PARTICLES + "buffstartparticle.ptl", label + " · 生效粒子", start, 1, z));
    }

    private static void addLayer(List<Visual> visuals, GameScript.Lookup lookup, String path, String label,
                                 int layer, long start, long end) throws IOException {
        if (end > start && lookup.read(path) != null) visuals.add(new Visual(path, label, layer, start, end, 0, 0));
    }

    private static long addClip(List<Visual> visuals, GameScript.Lookup lookup, String path, String label,
                                int layer, long start, int y) throws IOException {
        long end = start + duration(path, lookup);
        if (end > start) visuals.add(new Visual(path, label, layer, start, end, 0, y));
        return end;
    }

    private static long addParticle(List<SkillParticleEmitter> particles, GameScript.Lookup lookup, String path,
                                    String label, long start, long emission, int z) throws IOException {
        String text = lookup.read(path);
        if (text == null) return start;
        particles.add(new SkillParticleEmitter(path, label, start, emission, 0, 0, z));
        return start + emission + SkillParticleEmitter.tail(ParticleScript.parse(text));
    }

    private static long duration(String path, GameScript.Lookup lookup) throws IOException {
        String text = lookup.read(path);
        return text == null ? 0 : Math.min(10000, new AnimationTimeline(ResourceScript.animation(text)).duration());
    }

    private static long castingTime(List<GameScript.Field> fields, int level) {
        var values = GameScript.values(fields, "casting time");
        int first = number(values, 0, 500);
        int last = number(values, 1, first);
        int maximum = Math.max(1, GameScript.integer(GameScript.value(fields, "maximum level"), 1));
        double fraction = maximum == 1 ? 0 : (Math.max(1, Math.min(level, maximum)) - 1) / (double)(maximum - 1);
        return Math.max(0, Math.min(10000, Math.round(first + (last - (double)first) * fraction)));
    }

    private static long levelDuration(List<GameScript.Field> fields, int level) {
        var values = GameScript.values(fields, "level info");
        int width = number(values, 0, 0);
        long index = 1 + (Math.max(1, level) - 1L) * width;
        return width > 0 && index < values.size() ? Math.max(1, number(values, (int)index, 20000)) : 20000;
    }

    private static int number(List<String> values, int index, int fallback) {
        return index < values.size() ? GameScript.integer(values.get(index), fallback) : fallback;
    }

    /** 0x82C630 computes the cached height from the first body frame and its IMG trim. */
    static int standingHeight(CharacterAppearance.Model actor, GameScript.Lookup lookup,
                              ScenePreview.SpriteLookup sprites) throws IOException {
        int fallback = stockHeight(actor.job());
        if (!WAIT.equals(actor.action())) return fallback;
        for (var body : actor.parts()) {
            if (!body.slot().equals("body")) continue;
            try {
                String text = lookup.read(body.animation());
                if (text == null) return fallback;
                var frames = ResourceScript.animation(text).frames();
                if (frames.isEmpty() || frames.get(0).image().isEmpty()) return fallback;
                var first = frames.get(0);
                String image = NpkResources.substitute(first.image(), body.variation().stream().mapToInt(Integer::intValue).toArray());
                var trim = sprites.info(image, first.index());
                double offset = first.y() + trim.y();
                double height = -offset * Math.abs(first.scaleY());
                if (first.scaleY() < 0) height = Math.max(height, (offset + trim.height()) * -first.scaleY());
                return Math.abs(height) <= 10000 ? (int)height : fallback;
            } catch (GameScript.LimitExceeded limit) {
                throw limit;
            } catch (IOException | IllegalArgumentException unavailable) {
                return fallback;
            }
        }
        return fallback;
    }

    // First standing frame with the stock body IMG for each job.
    static int stockHeight(String job) {
        return switch (job) {
            case "mage" -> 84;
            case "fighter" -> 96;
            case "gunner" -> 127;
            case "swordman" -> 100;
            case "priest" -> 111;
            default -> throw new AssertionError(job);
        };
    }
}
