package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** ACT1 Mage casts and the original missile trail and monster appearances. */
final class Act1MageSupplement {
    private static final String MAGE = "character/mage/";
    private static final String EFFECT = MAGE + "effect/animation/";
    private static final String COMMON = "common/commoneffect/";
    private static final String PASSIVE = "passiveobject/character/mage/";
    private static final String MISSILE_TRAIL = PASSIVE + "particle/magicmissileparticle.ptl";
    private static final String MONSTER_LIST = "monster/monster.lst";
    private static final double RING_DIAMETER = 530;
    private static final String POKE_THROW = "skill/mage/pokethrow.skl";
    private static final String POKE_MISS = "mage-pokethrow:miss";
    private static final String TARGET_IT = "skill/mage/targetit.skl";
    private static final String TARGET_IT_HIT = "mage-targetit:hit";
    private static final String WHIP = "skill/mage/whip.skl";
    private static final String WHIP_HIT = "mage-whip:hit";
    private static final String HAND_STRIKE = "skill/mage/handstrike.skl";
    private static final String HAND_STRIKE_HIT = "mage-handstrike:hit";
    private static final String HAND_STRIKE_WALL = "mage-handstrike:wall";
    private static final String SACRIFICE = "skill/mage/sacrificespirit.skl";
    private static final String SUMMON_APPEARANCE = "mage-summon:appear";
    private static final String SACRIFICE_ALL = "mage-sacrifice:group";
    private static final String SACRIFICE_LIGHT_GROUP = "mage-sacrifice:light-group";

    private enum SacrificeSpirit {
        FIRE("fire", "亚德炎", "火焰爆炸", 51, 60007, 40002, 150),
        WATER("water", "冰奈斯", "冰柱", 52, 60006, 42007, 500),
        DARK("dark", "瑟冥特克", "暗影爆炸", 53, 60008, 44001, 125),
        LIGHT("light", "雷沃斯", "落雷", 54, 60009, 23019, 150);

        final String path;
        final String choice;
        final String name;
        final String effect;
        final int skillId;
        final int monsterId;
        final int objectId;
        final int defaultParameter;

        SacrificeSpirit(String element, String name, String effect, int skillId, int monsterId, int objectId, int defaultParameter) {
            this.path = "skill/mage/sacrificespiritattack" + element + ".skl";
            this.choice = "mage-sacrifice:" + element;
            this.name = name;
            this.effect = effect;
            this.skillId = skillId;
            this.monsterId = monsterId;
            this.objectId = objectId;
            this.defaultParameter = defaultParameter;
        }
    }

    private Act1MageSupplement() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level, String choice, Act1BuffCast.ActorHeight height,
                                  String weaponPath, ScenePreview.SpriteLookup sprites) throws IOException {
        if (choice.equals(SACRIFICE_LIGHT_GROUP) && (path.equals(SACRIFICE) || path.equals(SacrificeSpirit.LIGHT.path))) {
            if (!sacrificeAvailable(SacrificeSpirit.LIGHT, lookup)) return null;
            boolean passive = path.equals(SacrificeSpirit.LIGHT.path);
            var ritual = GameScript.fields(passive ? lookup.read(SACRIFICE) : text);
            var attack = GameScript.fields(passive ? text : lookup.read(SacrificeSpirit.LIGHT.path));
            return sacrifice(ritual, lookup, actions, passive ? 1 : level,
                    List.of(new SacrificeEntry(SacrificeSpirit.LIGHT, attack, passive ? level : 1, 0, 0)), true);
        }
        if (path.equals(SACRIFICE) && choice.equals(SACRIFICE_ALL)) {
            var ritual = GameScript.fields(text);
            int radius = sacrificeRadius(ritual, level);
            var spirits = new ArrayList<SacrificeEntry>();
            for (var spirit : SacrificeSpirit.values()) {
                if (!sacrificeAvailable(spirit, lookup)) return null;
                // A sample party of four owned spirits, all within the native ritual selection radius.
                int x = (2 * spirit.ordinal() - 3) * radius / 6;
                int y = (spirit.ordinal() % 2 == 0 ? -1 : 1) * radius * 29 / 400;
                spirits.add(new SacrificeEntry(spirit, GameScript.fields(lookup.read(spirit.path)), 1, x, y));
            }
            return sacrifice(ritual, lookup, actions, level, spirits, true);
        }
        for (var spirit : SacrificeSpirit.values()) {
            boolean passive = path.equals(spirit.path) && choice.isEmpty();
            if (!passive && !(path.equals(SACRIFICE) && choice.equals(spirit.choice))) continue;
            if (!sacrificeAvailable(spirit, lookup)) return null;
            String ritual = passive ? lookup.read(SACRIFICE) : text;
            String attack = passive ? text : lookup.read(spirit.path);
            if (ritual == null || attack == null) return null;
            return sacrifice(GameScript.fields(ritual), lookup, actions, passive ? 1 : level,
                    List.of(new SacrificeEntry(spirit, GameScript.fields(attack), passive ? level : 1, 0, 0)), false);
        }
        boolean pokeMiss = path.equals(POKE_THROW) && choice.equals(POKE_MISS);
        boolean targetItHit = path.equals(TARGET_IT) && choice.equals(TARGET_IT_HIT);
        boolean whipHit = path.equals(WHIP) && choice.equals(WHIP_HIT);
        boolean handStrikeWall = path.equals(HAND_STRIKE) && choice.equals(HAND_STRIKE_WALL);
        boolean handStrikeHit = handStrikeWall || path.equals(HAND_STRIKE) && choice.equals(HAND_STRIKE_HIT);
        int summonId = summonSkillId(path);
        boolean summonChoice = summonId >= 0 && (choice.equals(SUMMON_APPEARANCE)
                || choice.startsWith("mage-contract:") || choice.startsWith("mage-spirit:"));
        if (!choice.isEmpty() && !pokeMiss && !targetItHit && !whipHit && !handStrikeHit && !summonChoice) return null;
        int id = switch (path) {
            case "skill/mage/magicaltempoup.skl" -> 2;
            case "skill/mage/warpondamage.skl" -> 3;
            case "skill/mage/thrust.skl" -> 4;
            case "skill/mage/magicmissile.skl" -> 11;
            case "skill/mage/swing.skl" -> 13;
            case HAND_STRIKE -> 14;
            case WHIP -> 20;
            case "skill/mage/halloweenbuster.skl" -> 27;
            case "skill/mage/disenchant.skl" -> 28;
            case "skill/mage/elementalchain.skl" -> 29;
            case "skill/mage/followme.skl" -> 30;
            case "skill/mage/sunburst.skl" -> 31;
            case "skill/mage/mpregenrateup.skl" -> 41;
            case "skill/mage/provocationdoll.skl" -> 43;
            case SACRIFICE -> 49;
            case TARGET_IT -> 50;
            case "skill/mage/thunderstrike.skl" -> 55;
            case "skill/mage/wallofice.skl" -> 56;
            case "skill/mage/flamestrike.skl" -> 57;
            case "skill/mage/icefield.skl" -> 59;
            case POKE_THROW -> 65;
            case "skill/mage/randompiercestrike.skl" -> 66;
            case "skill/mage/strengthhandstrike.skl" -> 68;
            default -> summonId;
        };
        if (id < 0 || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/mageskill.lst")).get(id))) return null;
        var fields = GameScript.fields(text);
        return switch (id) {
            case 2 -> magicalTempoUp(fields, lookup, actions, level, height.read("mage"));
            case 3 -> warpOnDamage(fields, lookup, actions, level);
            case 11 -> magicMissile(fields, lookup, actions, level);
            case 4, 13, 20 -> melee(id, lookup, actions, weaponPath, whipHit);
            case 14 -> handStrike(fields, lookup, actions, level, handStrikeHit, handStrikeWall);
            case 27 -> halloweenBuster(fields, lookup, actions, level);
            case 28, 31 -> centeredCast(id, fields, lookup, actions, level, id == 31 ? height.read("mage") : 0);
            case 29, 30, 41 -> buff(id, fields, lookup, actions, level);
            case 43 -> provocationDoll(fields, lookup, actions, level);
            case 49 -> sacrifice(fields, lookup, actions, level, List.of(), false);
            case 50 -> targetIt(fields, lookup, actions, level, targetItHit, sprites);
            case 55 -> thunderStrike(fields, lookup, actions, level);
            case 56 -> iceWall(fields, lookup, actions, level);
            case 57 -> flameStrike(fields, lookup, actions, level);
            case 59 -> iceField(fields, lookup, actions, level);
            case 65 -> pokeThrow(fields, lookup, actions, level, weaponPath, sprites, pokeMiss);
            case 66 -> randomPierceStrike(fields, lookup, actions, weaponPath);
            case 68 -> strengthHandStrike(fields, lookup, actions);
            default -> summon(id, fields, lookup, actions, level, choice);
        };
    }

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (act1 && path.equals(SACRIFICE)) {
            var choices = new ArrayList<GameEffectRecipes.Effect>();
            for (var spirit : SacrificeSpirit.values()) {
                if (sacrificeAvailable(spirit, lookup)) {
                    choices.add(new GameEffectRecipes.Effect(spirit.choice, "献祭攻击 · " + spirit.name,
                            "", path, "在法阵内献祭一只" + spirit.name + "，对应献祭攻击 Lv.1"));
                }
            }
            if (choices.size() == SacrificeSpirit.values().length) {
                choices.add(new GameEffectRecipes.Effect(SACRIFICE_ALL, "献祭攻击 · 四属性精灵", "", path,
                        "演示范围内四只精灵在同一次释放中各自触发献祭攻击"));
            }
            if (sacrificeAvailable(SacrificeSpirit.LIGHT, lookup)) choices.add(sacrificeLightChoice(path));
            return List.copyOf(choices);
        }
        if (act1 && path.equals(SacrificeSpirit.LIGHT.path) && sacrificeAvailable(SacrificeSpirit.LIGHT, lookup)) {
            return List.of(sacrificeLightChoice(path));
        }
        int summonId = summonSkillId(path);
        if (act1 && summonId >= 0 && Act1SkillSequence.matches(path, "mage", summonId, lookup)) {
            var data = GameScript.values(GameScript.fields(lookup.read(path)), "static data");
            int monsterId = data.size() < 3 ? -1 : GameScript.integer(data.get(2), -1);
            String monster = monsterPath(monsterId, lookup);
            if (monster != null) {
                var choices = new ArrayList<GameEffectRecipes.Effect>();
                choices.addAll(Act1ContractAttacks.choices(path, monsterId, monster, lookup));
                choices.addAll(Act1SpiritAttacks.choices(path, monsterId, monster, lookup));
                if (!choices.isEmpty() || summonAttack(monsterId, monster, lookup) != null) {
                    choices.add(0, new GameEffectRecipes.Effect(SUMMON_APPEARANCE, "仅召唤与待机", "", path,
                            "演示召唤生效与召唤兽待机"));
                }
                return List.copyOf(choices);
            }
        }
        if (act1 && path.equals(TARGET_IT) && Act1SkillSequence.matches(path, "mage", 50, lookup)) {
            return List.of(new GameEffectRecipes.Effect(TARGET_IT_HIT, "魔力印记 · 命中", "", path,
                    "演示标记球命中、染色粒子和持续印记"));
        }
        if (act1 && path.equals(WHIP) && Act1SkillSequence.matches(path, "mage", 20, lookup)
                && lookup.read(EFFECT + "whiptomonsterhit.ani") != null) {
            return List.of(new GameEffectRecipes.Effect(WHIP_HIT, "鞭挞 · 命中", "", path,
                    "演示对敌人触发的独立鞭挞命中光"));
        }
        if (act1 && path.equals(HAND_STRIKE) && Act1SkillSequence.matches(path, "mage", 14, lookup)
                && !Act1SkillSequence.objectMotions(lookup, 23029).isEmpty()) {
            var hit = new GameEffectRecipes.Effect(HAND_STRIKE_HIT, "落花掌 · 命中", "", path,
                    "演示击中哥布林后附着的碰撞对象和击飞");
            if (!Act1WallImpact.available(lookup)) return List.of(hit);
            return List.of(hit, new GameEffectRecipes.Effect(HAND_STRIKE_WALL, "落花掌 · 满蓄撞墙", "", path,
                    "演示满蓄击飞哥布林后触发的撞墙冲击与反弹"));
        }
        if (!act1 || !path.equals(POKE_THROW)
                || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/mageskill.lst")).get(65))) return List.of();
        return List.of(new GameEffectRecipes.Effect(POKE_MISS, "圆舞棍 · 未抓到目标", "", path, "演示出棍落空后的收招"));
    }

    /** Ice Field uses object 23027's ground ring and its independent crystal scheduler. */
    private static Act1SkillSequence iceField(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int level) throws IOException {
        if (!hasThrow(actions, 2)) return null;
        var floor = Act1SkillSequence.objectMotions(lookup, 23027);
        if (floor.size() < 3) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 1000);
        long openTime = Math.max(1, Act1SkillSequence.staticValue(fields, 0, 300));
        long windup = Math.max(1, Act1SkillSequence.staticValue(fields, 1, 600));
        long recovery = Math.max(1, Act1SkillSequence.staticValue(fields, 2, 600));
        int count = Math.max(1, Math.min(8, Act1SkillSequence.staticValue(fields, 5, 8)));
        int x = Act1SkillSequence.staticValue(fields, 4, 350);
        int diameter = Act1SkillSequence.levelValue(fields, level, 2, 450, 1, 10000);
        double scale = diameter / 500.0;
        long open = cast + openTime;
        long ringEnd = open + (count - 1L) * (windup + recovery) + 100;
        var visuals = new ArrayList<Visual>();
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose("throw motion 2-1", 0, cast));
        poses.add(new Pose("throw motion 2-2", cast, open + recovery + (count > 1 ? windup : 0)));
        add(lookup, visuals, floor.get(0), "极冰盛宴 · 法阵展开", 280, cast, open, x, scale, openTime);
        add(lookup, visuals, floor.get(1), "极冰盛宴 · 法阵", 280, open, ringEnd, x, scale, 0);
        long end = add(lookup, visuals, floor.get(2), "极冰盛宴 · 法阵消散", 280, ringEnd, -1, x, scale, 0);
        for (int i = 0; i < count; i++) {
            long pulse = open + i * (windup + recovery);
            if (i > 0) {
                poses.add(new Pose("throw motion 2-2", pulse, pulse + recovery + (i + 1 < count ? windup : 0)));
            }
            end = Math.max(end, add(lookup, visuals, EFFECT + "icefieldblast.ani", "极冰盛宴 · 施放",
                    3501, pulse, pulse + recovery, 0, 1, 0));
        }
        int radius = diameter / 2;
        long area = Math.max(1, (long) radius * radius * 4 * 29 / 100);
        long crystalInterval = Math.max(1, 40_000_000 / area);
        int crystalRange = Math.max(0, radius - 90);
        var random = new Random(59);
        String folder = PASSIVE + "animation/icefield";
        for (int i = 0; i < 14; i++) {
            long crystal = open + i * crystalInterval;
            if (crystal >= ringEnd) break;
            // The client alternates quadrants and chooses a crystal and facing at random.
            double angle = Math.PI * random.nextDouble() / 2;
            double radiusSample = crystalRange * Math.sqrt(random.nextDouble());
            int dx = (int) (radiusSample * Math.cos(angle));
            int dy = (int) (radiusSample * Math.sin(angle)) * 29 / 100;
            double crystalX = x + (i % 2 == 0 ? dx : -dx);
            double crystalY = i / 2 % 2 == 0 ? dy : -dy;
            int variant = 1 + random.nextInt(2);
            long melt = crystal;
            for (String layer : List.of("normal" + variant, "dodge" + variant, "lighting", "dust1", "dust2")) {
                melt = Math.max(melt, add(lookup, visuals, folder + layer + ".ani", "极冰盛宴 · 冰柱", 3600,
                        crystal, -1, crystalX, crystalY, 1, 0));
            }
            for (int layer = 1; layer <= 4; layer++) {
                end = Math.max(end, add(lookup, visuals, folder + "disappear" + layer + ".ani", "极冰盛宴 · 冰柱消散",
                        3600 + layer, melt, -1, crystalX, crystalY, 1, 0));
            }
        }
        long recovered = open + (count - 1L) * (windup + recovery) + recovery;
        if (end > recovered && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", recovered, end));
        }
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(),
                "极冰盛宴演示短按法阵、基础攻击间隔和一组原版冰柱；冰柱位置、加速连按和满蓄效果由游戏决定");
    }

    /** State 10 maintains a selection area and launches one object 23020 per confirmation. */
    private static Act1SkillSequence thunderStrike(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                   Map<String, String> actions, int level) throws IOException {
        if (!hasThrow(actions, 4)) return null;
        var bolts = Act1SkillSequence.objectMotions(lookup, 23020);
        if (bolts.size() < 2) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 800);
        long recovery = Math.max(1, Act1SkillSequence.staticValue(fields, 0, 800));
        long cooldown = Math.max(recovery, Act1SkillSequence.staticValue(fields, 2, 1000));
        double x = Act1SkillSequence.staticValue(fields, 3, 0);
        double areaScale = Act1SkillSequence.staticValue(fields, 4, 500) / 302.0;
        int count = Act1SkillSequence.levelValue(fields, level, 0, 3, 1, 12);
        long finish = cast + (count - 1L) * cooldown;
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        if (cast > 0) poses.add(new Pose("throw motion 4-1", 0, cast));
        add(lookup, visuals, EFFECT + "thunderstrikecast.ani", "天雷 · 施法", 3501, 0, cast, 0, 1, 0);
        String area = EFFECT + "thunderstrikearea.ani";
        String areaText = lookup.read(area);
        if (areaText != null && finish > 0) {
            // ThunderStrikeArea is the top-left quarter of the ellipse. Reflect about its two authored seams.
            var frames = ResourceScript.animation(areaText).frames();
            var reflected = new HashMap<Integer, FrameOverride>();
            for (int i = 0; i < frames.size(); i++) {
                reflected.put(i, new FrameOverride(0, 0, null, 1, -1));
            }
            if (!frames.isEmpty()) {
                for (boolean reverse : List.of(false, true)) {
                    visuals.add(new Visual(area, "天雷 · 选点范围", 280, 0, finish, x, 0, null, areaScale, true,
                            new VisualStyle(null, 0, List.of(), Map.of(), reverse)));
                    visuals.add(new Visual(area, "天雷 · 选点范围", 280, 0, finish, x, 0, null, areaScale, true,
                            new VisualStyle(null, 0, List.of(), reflected, reverse)));
                }
            }
        }
        long end = finish + recovery;
        for (int i = 0; i < count; i++) {
            long start = cast + i * cooldown;
            poses.add(new Pose("throw motion 4-2", start, start + recovery));
            if (i + 1 < count && cooldown > recovery) {
                poses.add(new Pose("throw motion 4-1", start + recovery, start + cooldown));
            }
            add(lookup, visuals, EFFECT + "thunderstrikeblast.ani", "天雷 · 发令", 3501, start, -1, 0, 1, 0);
            end = Math.max(end, add(lookup, visuals, bolts.get(0), "天雷 · 落雷", 3600, start, -1, x, 1, 0));
            end = Math.max(end, add(lookup, visuals, bolts.get(1), "天雷 · 冲击", 3601, start, -1, x, 1, 0));
        }
        if (end > finish + recovery && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", finish + recovery, end));
        }
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(),
                "天雷以短按、光标留在起点并按冷却确认的顺序演示落雷；可移动选点和满蓄分支由游戏输入决定");
    }

    /** State 23 starts with the windup, repeats the base count of thrusts, and finishes with a sweep. */
    private static Act1SkillSequence randomPierceStrike(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                        Map<String, String> actions, String weaponPath) throws IOException {
        String windup = "etc motion:12";
        String loop = "etc motion:13";
        String finish = "etc motion:14";
        String character = MAGE + "mage.chr";
        var startFrames = Act1SkillSequence.frames(character, actions.get(windup), lookup);
        var loopFrames = Act1SkillSequence.frames(character, actions.get(loop), lookup);
        var finishFrames = Act1SkillSequence.frames(character, actions.get(finish), lookup);
        if (startFrames.isEmpty() || loopFrames.isEmpty() || finishFrames.isEmpty()) return null;
        long time = Act1SkillSequence.duration(startFrames, Integer.MAX_VALUE, null);
        long thrust = Act1SkillSequence.duration(loopFrames, Integer.MAX_VALUE, null);
        long firstThrust = Act1SkillSequence.duration(loopFrames, 1, null);
        long sweep = Act1SkillSequence.duration(finishFrames, Integer.MAX_VALUE, null);
        long dustCue = Act1SkillSequence.duration(finishFrames, 3, null);
        if (time <= 0 || thrust <= 0 || sweep <= 0) return null;
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        poses.add(new Pose(windup, 0, time));
        String folder = EFFECT + "randompiercestrike/";
        int count = Math.max(1, Math.min(24, Act1SkillSequence.staticValue(fields, 3, 6)));
        double reach = attackReach(lookup, weaponParts(lookup, weaponPath, loop));
        double sweepReach = attackReach(lookup, weaponParts(lookup, weaponPath, finish));
        for (int i = 0; i < count; i++) {
            poses.add(new Pose(loop, time, time + thrust));
            add(lookup, visuals, folder + "hit.ani", "流星闪影击 · 连刺", 3501, time + firstThrust, -1, reach - 35, -40, 1, 0);
            time += thrust;
        }
        long end = time + sweep;
        poses.add(new Pose(finish, time, end));
        add(lookup, visuals, folder + "swing.ani", "流星闪影击 · 横扫", 3501, time, end, 0, sweepReach / 215.0, 0);
        add(lookup, visuals, folder + "dust.ani", "流星闪影击 · 横扫烟尘", 3502, time + dustCue, end, 0, 1, 0);
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(),
                "流星闪影击演示基础次数的连续刺击与收招横扫；追加连按、取消和命中由游戏处理");
    }

    /** State 21 uses separate short-release and recovery phases for the stronger palm. */
    private static Act1SkillSequence strengthHandStrike(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                        Map<String, String> actions) throws IOException {
        String attack = "etc motion:15";
        String recover = "etc motion:16";
        String character = MAGE + "mage.chr";
        var body = Act1SkillSequence.frames(character, actions.get(attack), lookup);
        var back = Act1SkillSequence.frames(character, actions.get(recover), lookup);
        if (body.size() < 9 || back.isEmpty()) return null;
        long release = Act1SkillSequence.duration(body, 8, 1);
        long recovery = Act1SkillSequence.duration(body, Integer.MAX_VALUE, 1);
        long returned = recovery + Act1SkillSequence.duration(back, Integer.MAX_VALUE, null);
        var values = GameScript.values(fields, "static data");
        double speed = Act1SkillSequence.staticValue(fields, 0, 800);
        double acceleration = values.size() < 2 ? -500 : Math.max(-30000, Math.min(0, GameScript.integer(values.get(1), -500)));
        long travel = acceleration < 0 ? Math.min(recovery - release, Math.round(-1000 * speed / acceleration)) : recovery - release;
        var motion = new SkillMotion(release, travel, speed, acceleration);
        String folder = EFFECT + "strengthhandstrike/";
        var visuals = new ArrayList<Visual>();
        add(lookup, visuals, folder + "start_back.ani", "强袭流星打 · 起手后层", 3499, 0, release, 0, 1, 0);
        add(lookup, visuals, folder + "start_front.ani", "强袭流星打 · 起手前层", 3501, 0, release, 0, 1, 0);
        add(lookup, visuals, folder + "start_body_fade.ani", "强袭流星打 · 起点残影", 3498, release, -1, 0, 1, 0);
        if (lookup.read(folder + "attack_short.ani") != null) {
            visuals.add(new Visual(folder + "attack_short.ani", "强袭流星打 · 掌风", 3501, release, recovery,
                    0, 0, motion, 1, true));
        }
        if (lookup.read(folder + "attack_long.ani") != null) {
            visuals.add(new Visual(folder + "attack_long.ani", "强袭流星打 · 滑行", 3502, release, recovery,
                    0, 0, motion, 1, true));
        }
        double finalX = motion.offsetAt(recovery);
        long fadeEnd = add(lookup, visuals, folder + "end_body_fade.ani", "强袭流星打 · 终点残影",
                3498, recovery, -1, finalX, 1, 0);
        // ACT1 0x610F75 spawns Damage.ani at the caster once state 21 phase 1 finishes.
        long impactEnd = add(lookup, visuals, folder + "damage.ani", "强袭流星打 · 收势冲击",
                3501, recovery, -1, finalX, 1, 0);
        long end = Math.max(returned, Math.max(fadeEnd, impactEnd));
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(attack, 0, recovery, 1, false, motion));
        poses.add(new Pose(recover, recovery, returned, null, false, motion));
        if (end > returned && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", returned, end, null, false, motion));
        }
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(),
                "强袭流星打演示短按滑行、原版残影和收势；满蓄速度与命中取决于游戏输入和目标");
    }

    /** Object 23021 creates five object 23022 walls around its ground anchor. */
    private static Act1SkillSequence iceWall(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                             Map<String, String> actions, int level) throws IOException {
        if (!hasThrow(actions, 3) || GameEffectRecipes.passiveObject(lookup, 23021) == null) return null;
        String wall = GameEffectRecipes.passiveObject(lookup, 23022);
        if (wall == null) return null;
        var refs = GameScript.values(GameScript.fields(lookup.read(wall)), "etc motion");
        if (refs.size() < 7) return null;
        var layers = new ArrayList<String>();
        for (String ref : refs.subList(0, 7)) {
            String path = GameScript.resolve(wall, ref, lookup);
            if (path == null) return null;
            layers.add(path);
        }
        long cast = Act1SkillSequence.castingTime(fields, level, 1000);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 800));
        long life = Act1SkillSequence.levelValue(fields, level, 1, 4000, 1, 15000);
        long end = cast + life + 300;
        int radius = Math.max(0, Act1SkillSequence.staticValue(fields, 2, 300) / 2 - 20);
        int[] angles = {90, 10, 165, -60, -120};
        int[][] motions = {{1, 0, 2}, {4, 3}, {4, 3}, {6, 5}, {6, 5}};
        var fade = List.of(new VisualKeyframe(life, 1, 1), new VisualKeyframe(life + 300, 1, 0));
        var visuals = new ArrayList<Visual>();
        for (int i = 0; i < angles.length; i++) {
            double angle = Math.toRadians(angles[i]);
            int x = (int) (Math.cos(angle) * radius);
            int y = (int) (Math.sin(angle) * radius) * 29 / 100;
            var style = new VisualStyle(null, 0, fade, Map.of(), i == 2 || i == 4);
            int layer = y < 0 ? 3450 : 3550;
            for (int motion : motions[i]) {
                visuals.add(new Visual(layers.get(motion), "冰墙 · 冰壁", layer++, cast, end, x, y,
                        null, 1, true, style));
            }
        }
        return sequence(actions, 3, cast, recovery, end, visuals, List.of(),
                "冰墙演示五面冰壁的生成、维持与消散；被击破和减速由游戏战斗状态决定");
    }

    /** Objects 23013 and 23014 are the descending meteor and its separate ground explosion. */
    private static Act1SkillSequence halloweenBuster(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                     Map<String, String> actions, int level) throws IOException {
        if (!hasThrow(actions, 2)) return null;
        var motions = Act1SkillSequence.objectMotions(lookup, 23013);
        var explosion = Act1SkillSequence.objectMotions(lookup, 23014);
        String movement = lookup.read(MAGE + "particle/halloweenbuster.ptl");
        if (motions.size() < 3 || explosion.isEmpty() || movement == null) return null;
        var particle = ParticleScript.parse(movement);
        var horizontal = particle.axes().get(0);
        var vertical = particle.axes().get(2);
        if (!horizontal.type().equals("straight line") || !vertical.type().equals("straight line")
                || horizontal.start() <= 0 || vertical.start() >= 0) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 1200);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 800));
        double x = Act1SkillSequence.staticValue(fields, 1, 200);
        double speed = horizontal.start();
        double fall = vertical.start();
        // State 10 subtracts one second of X travel and sets initial Z to the negated Z velocity.
        long impact = cast + 1000;
        var motion = new SkillMotion(cast, impact - cast, speed, 0, fall, 0, -fall);
        var visuals = new ArrayList<Visual>();
        double meteorScale = Act1SkillSequence.levelValue(fields, level, 4, 1200, 1, 10000) / 1000.0;
        // Object 23013 installs ETC 0 behind the main pumpkin and ETC 1 in front (0x5B1310).
        visuals.add(new Visual(motions.get(1), "杰克降临 · 火焰后层", 3599, cast, impact, x - speed, 0,
                motion, meteorScale, true));
        visuals.add(new Visual(motions.get(0), "杰克降临 · 南瓜", 3600, cast, impact, x - speed, 0,
                motion, meteorScale, true));
        visuals.add(new Visual(motions.get(2), "杰克降临 · 火焰前层", 3601, cast, impact, x - speed, 0,
                motion, meteorScale, true));
        var emitters = new ArrayList<SkillParticleEmitter>();
        String folder = "passiveobject/common/particle/shootingstar";
        String fire = folder + "fire.ptl";
        String fireText = lookup.read(fire);
        if (fireText != null) {
            long aboveFloor = Math.max(1, Math.round(1000 * (1 + 30 / fall)));
            emitters.add(new SkillParticleEmitter(fire, "杰克降临 · 拖尾", cast, aboveFloor, 0,
                    x - speed, 0, 0, Double.NaN, meteorScale, false, 1, false, motion));
        }
        String explosionRoot = explosion.get(0);
        long emission = new AnimationTimeline(ResourceScript.animation(lookup.read(explosionRoot))).duration();
        if (emission <= 0) return null;
        long end = impact + emission;
        // 0x5B1200 scales the particles and ends the object when the unscaled root ANI ends.
        visuals.add(new Visual(explosionRoot, "杰克降临 · 爆炸判定", 3600, impact, end, x, 0));
        double scale = Act1SkillSequence.levelValue(fields, level, 6, 1000, 1, 10000) / 1000.0;
        for (String suffix : List.of("expsmoke", "exp", "expetc")) {
            String path = folder + suffix + ".ptl";
            String script = lookup.read(path);
            if (script == null) continue;
            emitters.add(new SkillParticleEmitter(path, "杰克降临 · 爆炸", impact, emission, 0, x, 0,
                    0, Double.NaN, scale));
            end = Math.max(end, impact + emission + SkillParticleEmitter.tail(ParticleScript.parse(script)));
        }
        return sequence(actions, 2, cast, recovery, end, visuals, List.copyOf(emitters),
                "杰克降临演示原版短按坠落、拖尾与地面爆炸；满蓄和提前碰撞取决于游戏输入及目标");
    }

    /** State 17 puts the decoy at the hit location and spawns the second cloud at the warp destination. */
    private static Act1SkillSequence warpOnDamage(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                  Map<String, String> actions, int level) throws IOException {
        String wait = "waiting motion";
        if (!actions.containsKey(wait)) return null;
        var objects = Act1SkillSequence.objectMotions(lookup, 23001);
        if (objects.isEmpty()) return null;
        long cloud = animationDuration(lookup, EFFECT + "warpcloud.ani");
        if (cloud == 0) return null;
        long end = Math.max(cloud, Act1SkillSequence.levelValue(fields, level, 0, 1000, 1, 3000));
        double distance = Act1SkillSequence.staticValue(fields, 1, 300);
        var destination = new SkillMotion(-1000, 1000, -distance, 0);
        var visuals = List.of(
                new Visual(EFFECT + "warpcloud.ani", "替身草人 · 起点烟雾", 3501, 0, cloud, 0, 0),
                new Visual(EFFECT + "warpcloud.ani", "替身草人 · 落点烟雾", 3501, 0, cloud, -distance, 0),
                new Visual(objects.get(0), "替身草人 · 留下草人", 3600, 0, end, 0, -50));
        return new Act1SkillSequence(List.of(new Pose(wait, 0, end, null, false, destination)), visuals, List.of(),
                "替身草人演示受击后无方向输入时的后移、两端烟雾与草人；拾取和无敌判定由游戏处理");
    }

    /** State 21 holds HandStrike at frame 7, then starts the slide on release. */
    private static Act1SkillSequence handStrike(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                Map<String, String> actions, int level, boolean hit,
                                                boolean wall) throws IOException {
        String action = "etc motion:4";
        String effect = EFFECT + "handstrike.ani";
        var body = Act1SkillSequence.frames(MAGE + "mage.chr", actions.get(action), lookup);
        var fx = Act1SkillSequence.frames(MAGE + "mage.chr", effect, lookup);
        if (body.size() < 9 || fx.size() < 9) return null;
        int hold = wall ? Math.max(1, Math.min(10000, Act1SkillSequence.staticValue(fields, 2, 500))) : 1;
        long release = Act1SkillSequence.duration(body, 8, hold);
        long end = Act1SkillSequence.duration(body, Integer.MAX_VALUE, hold);
        var values = GameScript.values(fields, "static data");
        double velocity = Act1SkillSequence.staticValue(fields, 0, 500);
        if (wall) velocity += Act1SkillSequence.staticValue(fields, 5, 200);
        double acceleration = values.size() < 2 ? -800 : Math.max(-30000, Math.min(0, GameScript.integer(values.get(1), -800)));
        long travel = acceleration < 0 ? Math.min(end - release, Math.round(-1000 * velocity / acceleration)) : end - release;
        var slide = new SkillMotion(release, travel, velocity, acceleration);
        // 0x6103F0 emits HandHit1/2 through 0x60BBB0 starting at stock frame 10, including on a miss.
        long contact = Act1SkillSequence.duration(body, 10, hold);
        double targetX = slide.offsetAt(contact) + 66;
        SkillMotion knockback = null;
        String volume = null;
        boolean bounced = false;
        long impact = contact;
        if (hit) {
            var objects = Act1SkillSequence.objectMotions(lookup, 23029);
            if (objects.isEmpty() || contact >= end) return null;
            volume = objects.get(0);
            long life = animationDuration(lookup, volume);
            if (life == 0) return null;
            int push = Act1SkillSequence.levelValue(fields, level, 2, 400, 0, 3000);
            int lift = Act1SkillSequence.levelValue(fields, level, 3, 200, 0, 1500);
            long flightTime = wall ? Act1WallImpact.airborne(0, lift) : life;
            knockback = new SkillMotion(contact, flightTime, wall ? push : 300, 0,
                    wall ? lift : life * .75, -Act1WallImpact.GRAVITY);
            bounced = wall && push > 0 && flightTime > 1 && Act1WallImpact.available(lookup);
            impact = bounced ? Act1WallImpact.contactTime(knockback) : contact + flightTime;
        }
        var motion = bounced ? Act1WallImpact.stopAtWall(slide, targetX + knockback.offsetAt(impact)) : slide;
        var style = new VisualStyle(null, 0, List.of(), Map.of(7, new Act1SkillSequence.FrameOverride(0, 0, hold)));
        long effectEnd = Act1SkillSequence.duration(fx, Integer.MAX_VALUE, hold);
        var palm = new Visual(effect, "落花掌 · 掌风", 3501, 0, Math.min(end, effectEnd), 0, 0, motion, 1, true, style);
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        poses.add(new Pose(action, 0, end, hold, false, motion));
        visuals.add(palm);
        long finish = end;
        if (wall) {
            // 0x6103F0 uses the common Mage charge overlay, variant 4, anchored at Z=106.
            long charging = Act1SkillSequence.duration(body, 7, null);
            for (int layer = 1; layer <= 2; layer++) {
                add(lookup, visuals, EFFECT + "magiconcharge4" + layer + ".ani", "落花掌 · 蓄气", 3501 + layer,
                        charging, release, 0, -106, 1, 0);
                String path = EFFECT + "magicfullcharge4" + layer + ".ani";
                long duration = animationDuration(lookup, path);
                if (duration > 0) {
                    visuals.add(new Visual(path, "落花掌 · 满蓄闪光", 3501 + layer, release, release + duration,
                            0, -106, motion, 1, true));
                    finish = Math.max(finish, release + duration);
                }
            }
        }
        if (contact < end) {
            for (int layer = 1; layer <= 2; layer++) {
                String path = MAGE + "effect/particle/handhit" + layer + ".ptl";
                String source = lookup.read(path);
                if (source == null) continue;
                emitters.add(new SkillParticleEmitter(path, "落花掌 · 掌风粒子 " + layer, contact, end - contact,
                        0, layer == 1 ? 57 : 27, 0, 63, Double.NaN, 1, true, 1, false, motion));
                finish = Math.max(finish, end + SkillParticleEmitter.tail(ParticleScript.parse(source)));
            }
        }
        String note = "落花掌演示短按的掌风、滑行和原版粒子，可切换命中效果。";
        if (knockback != null) {
            // 0x60F570 attaches #23029 to the struck target. The stock object has an image-less collision ANI.
            visuals.add(new Visual(volume, "落花掌 · 被击飞目标碰撞对象", 3551, contact, impact, targetX, 0,
                    knockback, 1, true));
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "落花掌 · 演示目标", 0, contact, targetX, null);
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "overturn", "落花掌 · 目标击飞",
                    contact, impact, targetX, knockback);
            if (bounced) {
                finish = Math.max(finish, Act1WallImpact.addRebound(visuals, lookup, fields, "落花掌", targetX, knockback, impact));
                note = "落花掌演示蓄满后击飞哥布林、撞墙和反弹。墙面位置为固定演示取样。";
            } else {
                finish = Math.max(finish, impact + 400);
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "down", "落花掌 · 目标落地",
                        impact, finish, targetX + knockback.offsetAt(impact), null);
                note = "落花掌演示命中后将哥布林击飞。受击轨迹为固定取样，原版碰撞对象为隐形碰撞体。";
            }
        }
        if (finish > end && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", end, finish, null, false, motion));
        }
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters), note);
    }

    /** State 22 attaches the grab streak to the weapon and switches to Smash on a successful grab. */
    private static Act1SkillSequence pokeThrow(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int level, String weaponPath,
                                              ScenePreview.SpriteLookup sprites, boolean miss) throws IOException {
        String attempt = "etc motion:6";
        String smash = "etc motion:7";
        String folder = EFFECT + "pokethrow/";
        var body = Act1SkillSequence.frames(MAGE + "mage.chr", actions.get(attempt), lookup);
        if (body.size() < 6) return null;
        var weapon = weaponParts(lookup, weaponPath, attempt);
        double tip = pokeReach(lookup, weapon, sprites);
        double speed = 1 + Act1SkillSequence.levelValue(fields, level, 2, 0, 0, 200) / 100.0;
        long attemptDuration = Math.round(Act1SkillSequence.duration(body, body.size(), null) / speed);
        // Use the second active attack frame for a repeatable target contact within the authored collision window.
        long caught = Math.round(Act1SkillSequence.duration(body, 5, null) / speed);
        long attemptEnd = miss ? attemptDuration : caught;
        var visuals = new ArrayList<Visual>();
        String streak = folder + "try.ani";
        long streakDuration = Math.round(animationDuration(lookup, streak) / speed);
        add(lookup, visuals, streak, "圆舞棍 · 抓取尝试", 3501, 0, Math.min(attemptEnd, streakDuration),
                tip, -41, 1, speed == 1 ? 0 : streakDuration);
        if (visuals.isEmpty()) return null;
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(attempt, 0, attemptEnd, null, speed != 1, null, 0, miss ? body.size() : 5));
        if (miss) {
            return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(),
                    "圆舞棍演示未抓到目标时的出棍与收招");
        }

        var release = Act1SkillSequence.frames(MAGE + "mage.chr", actions.get(smash), lookup);
        var impactClips = Act1SkillSequence.objectMotions(lookup, 23028);
        if (release.size() < 9 || impactClips.size() < 2) return null;
        long impact = caught + Act1SkillSequence.duration(release, 8, null);
        long releaseEnd = caught + Act1SkillSequence.duration(release, release.size(), null);
        poses.add(new Pose(smash, caught, releaseEnd));
        int targetHeight = Act1SwordAsuraSupplement.targetHeight(lookup, sprites);
        add(lookup, visuals, folder + "hit.ani", "圆舞棍 · 抓取命中", 3600, caught, -1, tip, -45, 1, 0);
        double smashScale = 1 + Math.max(0, Math.min(180, targetHeight - 70)) / 180.0;
        long end = Math.max(releaseEnd,
                add(lookup, visuals, folder + "smash.ani", "圆舞棍 · 反身砸地", 3501, caught, -1, 0, 2, smashScale, 0));
        for (int i = 0; i < impactClips.size(); i++) {
            end = Math.max(end, add(lookup, visuals, impactClips.get(i), "圆舞棍 · 落地冲击", 280 + i,
                    impact, -1, -73, 1, 0));
        }
        if (end > releaseEnd && actions.containsKey("waiting motion")) poses.add(new Pose("waiting motion", releaseEnd, end));
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "圆舞棍 · 抓取目标", 0, caught, tip, null);

        // CNPokeThrown (0x5456B0) snaps the grabbed target to the offsets of the caster's Smash frames.
        int halfHeight = targetHeight / 2;
        int firstHeight = Math.max(0, 43 - 2 * targetHeight / 3);
        int arcX = (int) (Math.cos(Math.PI / 6) * halfHeight);
        int arcZ = halfHeight / 2;
        long fourth = caught + Act1SkillSequence.duration(release, 4, null);
        long fifth = fourth + release.get(4).delay();
        long sixth = fifth + release.get(5).delay();
        long seventh = sixth + release.get(6).delay();
        addPokeTarget(visuals, lookup, "damage1", caught, fourth, tip, firstHeight, true);
        addPokeTarget(visuals, lookup, "damage1", fourth, fifth, tip - 22, firstHeight, true);
        addPokeTarget(visuals, lookup, "overturn", fifth, sixth, -34, Math.max(0, 98 - halfHeight), true);
        addPokeTarget(visuals, lookup, "down", sixth, seventh, arcX + 24, arcZ + 20, false);
        addPokeTarget(visuals, lookup, "down", seventh, impact, arcX - 85, Math.max(0, 15 - arcZ), false);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "down", "圆舞棍 · 砸地目标", impact, end, -73, null);
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(),
                "圆舞棍演示抓住预览怪物后的转身和向身后砸地");
    }

    private static void addPokeTarget(List<Visual> visuals, GameScript.Lookup lookup, String animation,
                                      long start, long end, double x, double z, boolean reverse) throws IOException {
        String path = Act1SwordAsuraSupplement.GOBLIN + animation + ".ani";
        if (end <= start || animationDuration(lookup, path) == 0) return;
        visuals.add(new Visual(path, "圆舞棍 · 被抓目标", 3550, start, end, x, -z, null, 1, true,
                new VisualStyle(null, 0, List.of(), Map.of(), reverse)));
    }

    private static List<CharacterAppearance.Part> weaponParts(GameScript.Lookup lookup, String weaponPath,
                                                              String action) throws IOException {
        if (weaponPath == null || weaponPath.isEmpty()) return List.of();
        var actor = CharacterAppearance.load(MAGE + "mage.chr", lookup.read(MAGE + "mage.chr"), lookup, Set.of(),
                "mage", action, Map.of("weapon", weaponPath));
        return actor.parts().stream().filter(part -> part.slot().equals("weapon")).toList();
    }

    private static double attackReach(GameScript.Lookup lookup, List<CharacterAppearance.Part> parts) throws IOException {
        long reach = 0;
        for (var part : parts) {
            for (var field : GameScript.fields(lookup.read(part.animation()))) {
                if (!field.tag().equals("attack box") || field.values().size() < 4) continue;
                int x = GameScript.integer(field.values().get(0), 0);
                int width = GameScript.integer(field.values().get(3), 0);
                reach = Math.max(reach, Math.min(10000L, (long) x + width));
            }
        }
        return reach;
    }

    /** State 22 measures frame 5's IMG trim and frame origin with 0x82C630. */
    private static double pokeReach(GameScript.Lookup lookup, List<CharacterAppearance.Part> parts,
                                    ScenePreview.SpriteLookup sprites) throws IOException {
        if (sprites == null) return attackReach(lookup, parts);
        double reach = 0;
        for (var part : parts) {
            String animation = lookup.read(part.animation());
            if (GameScript.values(GameScript.fields(animation), "attack box").isEmpty()) continue;
            var frames = ResourceScript.animation(animation).frames();
            if (frames.size() <= 5 || frames.get(5).image().isEmpty()) continue;
            var frame = frames.get(5);
            String image = NpkResources.substitute(frame.image(), part.variation().stream().mapToInt(Integer::intValue).toArray());
            try {
                var trim = sprites.info(image, frame.index());
                double left = frame.x() + trim.x();
                double right = left + trim.width();
                double edge = Math.max(left * frame.scaleX(), right * frame.scaleX());
                reach = Math.max(reach, (int) edge);
            } catch (IOException unavailable) {
                // If the IMG pack is missing, the ANI still provides its attack reach.
            }
        }
        return reach > 0 ? reach : attackReach(lookup, parts);
    }

    /** State 14 applies Mage skill 2 through 0x613CF0; status type 4 starts both PTLs in 0x52C030. */
    private static Act1SkillSequence magicalTempoUp(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                   Map<String, String> actions, int level, int height) throws IOException {
        String buff = "buff motion";
        String waiting = "waiting motion";
        if (!actions.containsKey(buff) || !actions.containsKey(waiting)) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 1000);
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        int chargeHeight = height + 30;
        add(lookup, visuals, COMMON + "animation/buffchargeglow.ani", "魔法秀 · 聚气", 3501,
                0, cast, 0, -chargeHeight, 1, 0);
        if (cast > 0) addParticle(lookup, emitters, COMMON + "particle/buffchargeparticle.ptl",
                "魔法秀 · 聚气粒子", 0, cast, 0, chargeHeight);
        long end = Act1BuffCast.activate("魔法秀", 34, cast, height, lookup, visuals, emitters);
        long active = Math.min(1800, Act1SkillSequence.levelValue(fields, level, 0, 20000, 1, 120000));
        for (int i = 1; i <= 2; i++) {
            String path = MAGE + "effect/particle/magicaltempoup" + i + ".ptl";
            String source = lookup.read(path);
            if (source == null) continue;
            // 0x52B810 positions the two emitters at 50% and 30% of the recipient's height.
            int z = i == 1 ? height / 2 : height * 3 / 10;
            emitters.add(new SkillParticleEmitter(path, "魔法秀 · 持续粒子", cast, active, 0,
                    0, 0, z, Double.NaN, 1, true));
            end = Math.max(end, cast + active + SkillParticleEmitter.tail(ParticleScript.parse(source)));
        }
        if (end <= cast) return null;
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose(buff, 0, cast, null, true, null));
        poses.add(new Pose(waiting, cast, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters),
                "魔法秀演示施法、状态图标与原版持续粒子");
    }

    /** State 14 uses the common cast charge, then 0x613CF0 activates the Mage status. */
    private static Act1SkillSequence buff(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                         Map<String, String> actions, int level) throws IOException {
        String buff = "buff motion";
        String waiting = "waiting motion";
        if (!actions.containsKey(buff) || !actions.containsKey(waiting)) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, id == 41 ? 800 : 700);
        int height = Act1BuffCast.stockHeight("mage");
        String name = switch (id) {
            case 29 -> "元素点燃";
            case 30 -> "召唤兽跟随";
            default -> "远古魔法书";
        };
        int icon = switch (id) {
            case 29 -> 28;
            case 30 -> 31;
            default -> 29;
        };
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        add(lookup, visuals, COMMON + "animation/buffchargeglow.ani", name + " · 聚气", 3700, 0, cast,
                0, -height - 30, 1, 0);
        if (cast > 0) addParticle(lookup, emitters, COMMON + "particle/buffchargeparticle.ptl", name + " · 聚气",
                0, cast, 0, height + 30);
        long end = Act1BuffCast.activate(name, icon, cast, height, lookup, visuals, emitters);
        String note;
        switch (id) {
            case 29 -> {
                end = Math.max(end, add(lookup, visuals, EFFECT + "elementalchain/start.ani", name + " · 生效",
                        3501, cast, -1, 0, -25, 1, 0));
                note = "元素点燃演示初次施法和生效；四属性标记在后续施放对应元素技能时点亮";
            }
            case 30 -> {
                var body = Act1SkillSequence.frames(MAGE + "mage.chr", actions.get(buff), lookup);
                long cue = Math.min(cast, Act1SkillSequence.duration(body, 2, (int) cast));
                end = Math.max(end, add(lookup, visuals, EFFECT + "followmecast.ani", name + " · 发令",
                        3501, cue, -1, 17, -81, 1, 0));
                note = "召唤兽跟随演示施法、发令与生效图标；召唤兽的旗标与移动需要场上已有召唤兽";
            }
            default -> {
                long opened = add(lookup, visuals, EFFECT + "mpregenrateupstart.ani", name + " · 生效", 3501,
                        cast, -1, 0, 1, 0);
                end = Math.max(end, opened);
                long duration = Act1SkillSequence.levelValue(fields, level, 0, 20000, 1, 120000);
                long finish = cast + Math.min(1800, duration);
                if (finish > opened && opened > cast) {
                    end = Math.max(end, add(lookup, visuals, EFFECT + "mpregenrateuploop.ani", name + " · 持续",
                            3501, opened, finish, 0, 1, 0));
                }
                note = "远古魔法书演示施法与自身魔力回复状态的生效；实际回复量由技能等级决定";
            }
        }
        if (end <= cast) return null;
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose(buff, 0, cast, null, true, null));
        poses.add(new Pose(waiting, cast, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters), note);
    }

    private static boolean sacrificeAvailable(SacrificeSpirit spirit, GameScript.Lookup lookup) throws IOException {
        return Act1SkillSequence.matches(SACRIFICE, "mage", 49, lookup)
                && Act1SkillSequence.matches(spirit.path, "mage", spirit.skillId, lookup)
                && lookup.read(SACRIFICE) != null && lookup.read(spirit.path) != null
                && monsterIdle(spirit.monsterId, lookup) != null
                && !Act1SkillSequence.objectMotions(lookup, spirit.objectId).isEmpty();
    }

    private record SacrificeEntry(SacrificeSpirit spirit, List<GameScript.Field> fields, int level, int x, int y) {}

    private static GameEffectRecipes.Effect sacrificeLightChoice(String path) {
        return new GameEffectRecipes.Effect(SACRIFICE_LIGHT_GROUP, "雷沃斯献祭 · 多目标落雷", "", path,
                "演示一次扫描中在范围内每个敌人位置生成落雷，圈外目标留作对照");
    }

    private static int sacrificeRadius(List<GameScript.Field> fields, int level) {
        return Act1SkillSequence.levelValue(fields, level, 0, 400, 1, 10000) / 2;
    }

    /** 0x614D40 displays the circle, then puts the caster's spirits inside it into state 20 on release. */
    private static Act1SkillSequence sacrifice(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int level, List<SacrificeEntry> spirits,
                                              boolean multiTarget) throws IOException {
        if (!hasThrow(actions, 4)) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 800);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 2, 700));
        double x = Act1SkillSequence.staticValue(fields, 0, 200);
        double scale = Act1SkillSequence.levelValue(fields, level, 0, 400, 1, 10000) / 140.0;
        var visuals = new ArrayList<Visual>();
        // State 10 uses the forward SKL offset with the ground-coordinate flag set.
        add(lookup, visuals, EFFECT + "sacrificespirit1.ani", "精灵献祭 · 法阵", 280, 0, cast, x, scale, 0);
        add(lookup, visuals, EFFECT + "sacrificespirit2.ani", "精灵献祭 · 法阵", 281, 0, cast, x, scale, 0);
        if (visuals.isEmpty()) return null;
        if (spirits.isEmpty()) {
            return sequence(actions, 4, cast, recovery, recovery, visuals, List.of(),
                    "精灵献祭演示场上没有可献祭精灵时的原版施法法阵；献祭和生效效果需要范围内已有精灵");
        }
        var emitters = new ArrayList<SkillParticleEmitter>();
        long end = recovery;
        for (var entry : spirits) {
            end = Math.max(end, appendSacrifice(lookup, visuals, emitters, entry, cast, recovery, x, multiTarget));
        }
        String note;
        if (spirits.size() > 1) {
            note = "四只已召唤精灵均在法阵实际搜索范围内。释放时各自在当前位置触发对应献祭攻击。";
        } else if (multiTarget) {
            note = "雷沃斯在一次扫描中对范围内的敌人同时生成落雷。演示放置三个圈内哥布林和一个圈外哥布林。";
        } else {
            var spirit = spirits.get(0).spirit();
            note = "在法阵中心放置一只" + spirit.name + "，施法结束时触发献祭攻击。";
            if (spirit == SacrificeSpirit.WATER) note += "冰柱演示释放后 1 秒的八向运动。";
            if (spirit == SacrificeSpirit.LIGHT) note += "落雷目标放在雷沃斯搜索半径内。";
        }
        return sequence(actions, 4, cast, recovery, end, visuals, List.copyOf(emitters), note);
    }

    private static long appendSacrifice(GameScript.Lookup lookup, List<Visual> visuals,
                                       List<SkillParticleEmitter> emitters, SacrificeEntry entry, long cast,
                                       long recovery, double circleX, boolean multiTarget) throws IOException {
        var spirit = entry.spirit();
        double x = circleX + entry.x();
        int y = entry.y();
        String monster = monsterPath(spirit.monsterId, lookup);
        var monsterFields = GameScript.fields(lookup.read(monster));
        int elevation = GameScript.integer(GameScript.value(monsterFields, "floating height"), 0);
        String idle = GameScript.resolve(monster, GameScript.value(monsterFields, "waiting motion"), lookup);
        long idleDuration = animationDuration(lookup, idle);
        if (cast > 0 && idleDuration > 0) {
            visuals.add(new Visual(idle, spirit.name, 3550, 0, cast, x, y, null, 1, true,
                    new VisualStyle(null, 0, List.of(), Map.of(), false, idleDuration)).withoutAttack());
        }
        var motions = Act1SkillSequence.objectMotions(lookup, spirit.objectId);
        String label = "献祭攻击 · " + spirit.effect;
        int parameter = Act1SkillSequence.levelValue(entry.fields(), entry.level(), 0, spirit.defaultParameter, 1, 3000);
        long end;
        switch (spirit) {
            case WATER -> end = sacrificeIce(visuals, motions, lookup, label, cast, x, y, parameter);
            case LIGHT -> end = sacrificeLightning(visuals, lookup, motions.get(0), label, cast, recovery, x, y,
                    parameter, multiTarget);
            default -> {
                // 0x6B9E30 / 0x6BA370 spawn the burst at the spirit's position plus half its body height.
                var idleFrames = ResourceScript.animation(lookup.read(idle)).frames();
                double bodyHeight = idleFrames.isEmpty() ? 0 : idleFrames.get(0).combat().hurt().stream()
                        .mapToDouble(box -> box.z() + box.height()).max().orElse(0);
                double z = elevation + Math.max(0, bodyHeight) / 2;
                double burstScale = parameter / 100.0;
                end = add(lookup, visuals, motions.get(0), label, 3600, cast, -1, x, y - z, burstScale, 0);
                long burstDuration = end - cast;
                String object = GameEffectRecipes.passiveObject(lookup, spirit.objectId);
                for (String reference : GameScript.values(GameScript.fields(lookup.read(object)), "string data")) {
                    String particle = GameScript.resolve(object, reference, lookup);
                    if (particle == null || !particle.endsWith(".ptl")) continue;
                    var script = ParticleScript.parse(lookup.read(particle));
                    emitters.add(new SkillParticleEmitter(particle, label, cast, burstDuration, 0, x, y, z,
                            Double.NaN, burstScale));
                    end = Math.max(end, cast + burstDuration + SkillParticleEmitter.tail(script));
                }
            }
        }
        return end;
    }

    private record SacrificeTarget(int x, int y) {}

    /** 0x6BA680 scans enemies with 0x471900's normalized ground distance and spawns #23019 on each match. */
    private static long sacrificeLightning(List<Visual> visuals, GameScript.Lookup lookup, String motion, String label,
                                           long cast, long recovery, double x, int y, int radius,
                                           boolean multiTarget) throws IOException {
        int depth = radius * 29 / 200;
        var middle = new SacrificeTarget(radius / 2, 0);
        var targets = multiTarget ? List.of(new SacrificeTarget(-radius * 3 / 4, -depth), middle,
                new SacrificeTarget(radius / 5, depth), new SacrificeTarget(radius * 3 / 2 + 30, 0))
                : List.of(middle);
        var range = new CombatDebug.TargetRange(radius, null, 0);
        long boltEnd = cast + animationDuration(lookup, motion);
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            double targetX = x + target.x();
            double targetY = y + target.y();
            boolean selected = range.contains(target.x(), target.y(), 0);
            if (selected) add(lookup, visuals, motion, label, 3600, cast, boltEnd, targetX, targetY, 1, 0);
            String name = "献祭落雷 · 哥布林 " + (i + 1) + (selected ? "" : "（范围外）");
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", name, 0, Math.max(recovery, boltEnd),
                    targetX, targetY, null);
        }
        return boltEnd;
    }

    /** 0x6B9B50 launches 42007 every 45 degrees. 0x5C58F0 attaches successive ANI at a speed-dependent interval. */
    private static long sacrificeIce(List<Visual> visuals, List<String> motions, GameScript.Lookup lookup,
                                     String label, long start, double x, int y, int speed) throws IOException {
        long end = start + 1000;
        String root = motions.get(0);
        long loop = animationDuration(lookup, root);
        if (loop <= 0) return start;
        var velocity = new ParticleScript.Axis("straight line", speed, speed, 0, 0);
        var stationary = new ParticleScript.Axis("stop", 0, 0, 0, 0);
        int interval = Math.max(50, 200 - Math.max(0, speed - 100) / 6);
        var style = new VisualStyle(null, 0, List.of(), Map.of(), false, loop);
        long finish = end;
        for (int angle = 0; angle < 360; angle += 45) {
            var motion = SkillMotion.orbit(start, end - start, new SkillMotion.Orbit(velocity, stationary, 0, "",
                    angle, null, true));
            visuals.add(new Visual(root, label, 3600, start, end, x, y, motion, 1, true, style));
            if (motions.size() < 2) continue;
            for (int i = 0; start + (long) i * interval < end; i++) {
                long time = start + (long) i * interval;
                String spike = motions.get(1 + i % (motions.size() - 1));
                finish = Math.max(finish, add(lookup, visuals, spike, label, 3601, time, -1,
                        x + motion.offsetAt(time), y + motion.offsetYAt(time), 1, 0));
            }
        }
        return finish;
    }

    /** Object 23023 plays the ground warning before the main burst, then attaches dust and two PTLs. */
    private static Act1SkillSequence flameStrike(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                 Map<String, String> actions, int level) throws IOException {
        if (!hasThrow(actions, 2) || GameEffectRecipes.passiveObject(lookup, 23023) == null) return null;
        String folder = PASSIVE + "animation/flamestrike/";
        if (animationDuration(lookup, folder + "ground-normal.ani") == 0
                || animationDuration(lookup, folder + "blast-normal.ani") == 0) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 700);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 600));
        double x = Act1SkillSequence.staticValue(fields, 1, 150);
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        long burst = add(lookup, visuals, folder + "ground-normal.ani", "烈焰冲击 · 地面预警", 280, cast, -1, x, 1, 0);
        add(lookup, visuals, folder + "ground-dodge.ani", "烈焰冲击 · 地面预警", 281, cast, burst, x, 1, 0);
        long end = burst;
        for (String layer : List.of("gbla-normal", "gbla-dodge", "blast-normal", "blast-dodge", "dust-normal", "dust-dodge")) {
            int order = layer.startsWith("gbla") ? 282 : 3600;
            end = Math.max(end, add(lookup, visuals, folder + layer + ".ani", "烈焰冲击 · 爆发", order,
                    burst, -1, x, 1, 0));
        }
        for (int index = 1; index <= 2; index++) {
            String path = PASSIVE + "particle/flamestrikeparticle-" + index + ".ptl";
            end = Math.max(end, addParticle(lookup, emitters, path, "烈焰冲击 · 火花", burst, 1, x, 0));
        }
        return sequence(actions, 2, cast, recovery, end, visuals, List.copyOf(emitters),
                "烈焰冲击演示光标未移动时的原版地面预警与爆发；可移动的选点和受击由游戏处理");
    }

    private static long addParticle(GameScript.Lookup lookup, List<SkillParticleEmitter> emitters, String path,
                                    String label, long start, long active, double x, double z) throws IOException {
        String script = lookup.read(path);
        if (script == null) return start;
        emitters.add(new SkillParticleEmitter(path, label, start, active, 0, x, z));
        return start + active + SkillParticleEmitter.tail(ParticleScript.parse(script));
    }

    /** Object 23017 throws the doll and creates monster 60010 when it reaches the ground. */
    private static Act1SkillSequence provocationDoll(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                     Map<String, String> actions, int level) throws IOException {
        if (!hasThrow(actions, 1)) return null;
        var objects = Act1SkillSequence.objectMotions(lookup, 23017);
        String movement = lookup.read(MAGE + "particle/provocationdoll.ptl");
        String idle = monsterIdle(60010, lookup);
        if (objects.isEmpty() || movement == null || idle == null) return null;
        var particle = ParticleScript.parse(movement);
        var horizontal = particle.axes().get(0);
        var vertical = particle.axes().get(2);
        if (!horizontal.type().equals("straight line") || !vertical.type().equals("straight line") || particle.gravity() <= 0) {
            return null;
        }
        long cast = Act1SkillSequence.castingTime(fields, level, 800);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 700));
        double height = 33;
        double rise = vertical.start();
        double gravity = particle.gravity();
        long flight = Math.round(1000 * (rise + Math.sqrt(rise * rise + 2 * gravity * height)) / gravity);
        var motion = new SkillMotion(cast, flight, horizontal.start(), 0, rise, -gravity, height);
        long landed = cast + flight;
        double x = 50 + motion.offsetAt(landed);
        long idleDuration = animationDuration(lookup, idle);
        if (idleDuration == 0) return null;
        long end = Math.max(recovery, landed) + Math.min(3000, idleDuration);
        var visuals = new ArrayList<Visual>();
        visuals.add(new Visual(objects.get(0), "舒露露 · 抛出", 3600, cast, landed, 50, 0, motion, 1, true));
        visuals.add(new Visual(idle, "舒露露 · 落地", 3550, landed, end, x, 0, null, 1, true,
                new VisualStyle(null, 0, List.of(), Map.of(), false, idleDuration)));
        int radius = Act1SkillSequence.levelValue(fields, level, 1, 400, 1, 10000) / 2;
        double areaScale = radius / 160.0;
        // CNProvocationDollAura draws both symbol layers with the same radius scale as the ring.
        add(lookup, visuals, EFFECT + "provocationdollsymbol1.ani", "舒露露 · 挑衅标记", 279,
                landed, end, x, areaScale, 0);
        add(lookup, visuals, EFFECT + "provocationdollsymbol2.ani", "舒露露 · 挑衅标记", 280,
                landed, end, x, areaScale, 0);
        long area = add(lookup, visuals, EFFECT + "provocationdollareastart.ani", "舒露露 · 挑衅范围", 281,
                landed, -1, x, areaScale, 0);
        String loop = EFFECT + "provocationdollarealoop.ani";
        long areaDuration = animationDuration(lookup, loop);
        if (area < end && areaDuration > 0) {
            visuals.add(new Visual(loop, "舒露露 · 挑衅范围", 281, area, end, x, 0, null, areaScale, true,
                    new VisualStyle(null, 0, List.of(), Map.of(), false, areaDuration)));
        }
        return sequence(actions, 1, cast, recovery, end, visuals, List.of(),
                "舒露露演示抛出、落地与挑衅范围；引怪和人偶消失由游戏战斗状态决定");
    }

    /** Mage states 18 and 20 attach the thrust and whip on entry, and the wide swing on actor frame 3. */
    private static Act1SkillSequence melee(int id, GameScript.Lookup lookup, Map<String, String> actions,
                                           String weaponPath, boolean whipHit) throws IOException {
        int index = switch (id) {
            case 4 -> 1;
            case 13 -> 3;
            default -> 5;
        };
        String action = "etc motion:" + index;
        var body = Act1SkillSequence.frames(MAGE + "mage.chr", actions.get(action), lookup);
        long end = Act1SkillSequence.duration(body, Integer.MAX_VALUE, null);
        if (end == 0) return null;
        var visuals = new ArrayList<Visual>();
        String note;
        if (id == 4) {
            // State 18 places both streaks 5 px inside the weapon's farthest attack box.
            double tip = attackReach(lookup, weaponParts(lookup, weaponPath, action)) - 5;
            add(lookup, visuals, EFFECT + "thrustunder.ani", "龙牙 · 刺击后层", 499, 0, end, tip, -41, 1, 0);
            add(lookup, visuals, EFFECT + "thrustover.ani", "龙牙 · 刺击前层", 3501, 0, end, tip, -41, 1, 0);
            note = "龙牙按当前武器的攻击范围定位两层刀光";
        } else if (id == 13) {
            long swing = Act1SkillSequence.duration(body, 3, null);
            add(lookup, visuals, EFFECT + "swing.ani", "碎霸 · 横扫", 3501, swing, end, 0, 1, 0);
            note = "碎霸在原版角色动作的挥击帧显示刀光";
        } else {
            add(lookup, visuals, EFFECT + "whiptomonsterswing.ani", "鞭挞 · 挥击", 3501, 0, end, 0, 1, 0);
            note = "鞭挞演示原版挥击；命中召唤兽后的加速效果由游戏处理";
            if (whipHit) {
                // 0x60F570 spawns the hit ANI before testing whether the recipient is an allied summon.
                if (body.size() <= 4) return null;
                long contact = Act1SkillSequence.duration(body, 4, null);
                double x = 100;
                long hitEnd = add(lookup, visuals, EFFECT + "whiptomonsterhit.ani", "鞭挞 · 命中光", 3552,
                        contact, -1, x, -40, 1, 0);
                if (hitEnd == contact) return null;
                end = Math.max(end, hitEnd);
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "鞭挞 · 演示目标", 0, contact, x, null);
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "鞭挞 · 目标受击", contact, end, x, null);
                note = "鞭挞用原版第四号动作帧的攻击盒演示敌人受击与独立命中光。";
            }
        }
        if (visuals.isEmpty()) return null;
        return new Act1SkillSequence(List.of(new Pose(action, 0, end)), List.copyOf(visuals), List.of(), note);
    }

    /** State 10 releases object 23018 from the one-hand cast at X44 and Z60. */
    private static Act1SkillSequence targetIt(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int level, boolean hit,
                                              ScenePreview.SpriteLookup sprites) throws IOException {
        if (!hasThrow(actions, 1)) return null;
        var objects = Act1SkillSequence.objectMotions(lookup, 23018);
        String movement = lookup.read(MAGE + "particle/targetitball.ptl");
        if (objects.isEmpty() || movement == null) return null;
        var axis = ParticleScript.parse(movement).axes().get(0);
        if (!axis.type().equals("straight line") || axis.start() <= 0) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 500);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 500));
        long flight = Act1SkillSequence.levelValue(fields, level, 5, 450, 1, 10000);
        long travel = hit ? Math.max(1, Math.min(300, flight / 2)) : flight;
        long contact = cast + travel;
        var motion = new SkillMotion(cast, travel, axis.start(), 0);
        var ball = new Visual(objects.get(0), "魔力印记 · 标记球", 3600, cast, contact, 44, -60, motion, 1, true);
        if (!hit) {
            return sequence(actions, 1, cast, recovery, contact, List.of(ball), List.of(),
                    "魔力印记演示无目标时标记球的飞行，可切换命中效果。");
        }
        // 0x5B4680 emits the paint PTL once and calls 0x60ED70 to attach the separate target status.
        String paint = PASSIVE + "particle/targetitballpaint.ptl";
        String paintSource = lookup.read(paint);
        String mark = EFFECT + "targetit.ani";
        if (paintSource == null || animationDuration(lookup, mark) == 0) return null;
        long markLife = Act1SkillSequence.levelValue(fields, level, 0, 5000, 1, 30000);
        long end = contact + Math.max(markLife, SkillParticleEmitter.tail(ParticleScript.parse(paintSource)));
        double targetX = 44 + motion.offsetAt(contact);
        var visuals = new ArrayList<Visual>();
        visuals.add(ball);
        visuals.add(new Visual(mark, "魔力印记 · 持续标记", 3700, contact, contact + markLife,
                targetX, -Act1SwordAsuraSupplement.targetHeight(lookup, sprites)));
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "魔力印记 · 演示目标", 0, contact, targetX, null);
        long react = Math.min(end, contact + 300);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "魔力印记 · 目标受击", contact, react, targetX, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "魔力印记 · 已标记目标", react, end, targetX, null);
        var emitter = new SkillParticleEmitter(paint, "魔力印记 · 命中染色", contact, 1, 0,
                targetX, 0, 60, Double.NaN, 1, true);
        return sequence(actions, 1, cast, recovery, end, visuals, List.of(emitter),
                "魔力印记用一名哥布林演示命中、染色粒子及当前等级的印记时长。持续伤害和召唤兽集火由实战处理。");
    }

    /** CNMagicMissile attaches this PTL to object 23007. Each particle starts at its birth position. */
    private static Act1SkillSequence magicMissile(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                  Map<String, String> actions, int level) throws IOException {
        if (!hasThrow(actions, 1)) return null;
        var motions = Act1SkillSequence.objectMotions(lookup, 23007);
        String movement = lookup.read(MAGE + "particle/magicmissile.ptl");
        String trailText = lookup.read(MISSILE_TRAIL);
        if (motions.isEmpty() || movement == null || trailText == null) return null;
        var axis = ParticleScript.parse(movement).axes().get(0);
        var trail = ParticleScript.parse(trailText);
        if (!axis.type().equals("straight line") || axis.start() <= 0) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 420);
        long flight = Act1SkillSequence.levelValue(fields, level, 2, 520, 1, 10000);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 300));
        var motion = new SkillMotion(cast, flight, axis.start(), 0);
        var visuals = new ArrayList<Visual>();
        add(lookup, visuals, EFFECT + "onehandthrow1.ani", "魔法星弹 · 施法", 3501, 0, cast, 0, 1, cast);
        add(lookup, visuals, EFFECT + "onehandthrow2.ani", "魔法星弹 · 出手", 3501, cast, -1, 0, 1, 0);
        visuals.add(new Visual(motions.get(0), "魔法星弹 · 飞行", 3600, cast, cast + flight, 44, -60,
                motion, 1, true));
        var emitter = new SkillParticleEmitter(MISSILE_TRAIL, "魔法星弹 · 拖尾", cast, flight, 0,
                44, 0, 60, Double.NaN, 1, false, 1, false, motion);
        return sequence(actions, 1, cast, recovery, cast + flight + SkillParticleEmitter.tail(trail), visuals,
                List.of(emitter), "魔法星弹演示短按、水平飞行和原版拖尾；蓄力、方向修正和追踪由游戏输入及目标位置决定");
    }

    private static int summonSkillId(String path) {
        return switch (path) {
            case "skill/mage/summonspiritfire.skl" -> 21;
            case "skill/mage/summonspiritwater.skl" -> 22;
            case "skill/mage/summonspiritdark.skl" -> 23;
            case "skill/mage/summonspiritlight.skl" -> 24;
            case "skill/mage/summonhodor.skl" -> 25;
            case "skill/mage/summonsandor.skl" -> 44;
            case "skill/mage/summonauxo.skl" -> 45;
            case "skill/mage/summonluis.skl" -> 46;
            case "skill/mage/summonecheverra.skl" -> 47;
            default -> -1;
        };
    }

    record SummonAttackResult(long bodyEnd, long end, double bodyX, boolean despawned) {
        SummonAttackResult(long bodyEnd, long end, double bodyX) {
            this(bodyEnd, end, bodyX, false);
        }
    }

    private record SummonLayer(String path, int order) {}

    private record SummonAttack(String path, List<SummonLayer> equipment, long duration, double targetOffset) {}

    private static SummonAttack summonAttack(int monsterId, String monster, GameScript.Lookup lookup) throws IOException {
        var motion = GameScript.values(GameScript.fields(lookup.read(monster)), "attack motion");
        if (motion.isEmpty()) return null;
        String path = GameScript.resolve(monster, motion.get(0), lookup);
        if (path == null) return null;
        var frames = ResourceScript.animation(lookup.read(path)).frames();
        // This preview selects the MOB's first attack, using its own contact boxes and animation clock.
        var box = frames.stream().flatMap(frame -> frame.combat().attack().stream()).findFirst().orElse(null);
        var equipment = summonEquipment(monsterId, "attack motion", lookup);
        if (box == null) {
            for (var layer : equipment) {
                box = ResourceScript.animation(lookup.read(layer.path())).frames().stream()
                        .flatMap(frame -> frame.combat().attack().stream())
                        .filter(candidate -> candidate.x() + candidate.width() / 2 >= 0)
                        .findFirst().orElse(null);
                if (box != null) break;
            }
        }
        if (box == null) return null;
        return new SummonAttack(path, equipment, Act1SkillSequence.duration(frames, Integer.MAX_VALUE, null),
                box.x() + box.width() / 2);
    }

    private static List<SummonLayer> summonEquipment(int monsterId, String action, GameScript.Lookup lookup) throws IOException {
        // Constructors load the stock EQU parts in attachment order.
        String prefix;
        List<String> parts;
        if (monsterId == 900) {
            prefix = "equipment/monster/goblin/";
            parts = List.of("acc9", "acc9-2", "acc9-3", "bcc9", "hcc9", "weapon9");
        } else if (monsterId == 901) {
            prefix = "equipment/monster/soldier/sandorexpeller/sandorexpeller";
            parts = List.of("leg", "head", "rightarm", "leftarm", "headend", "glow");
        } else if (monsterId == 902) {
            prefix = "equipment/monster/soceress/luis_";
            parts = List.of("hair01", "cloth01", "weapon01", "acc01", "weapon02");
        } else {
            return List.of();
        }
        var layers = new ArrayList<SummonLayer>();
        for (String part : parts) {
            String equipment = prefix + part + ".equ";
            int layer = 0;
            for (var field : GameScript.fields(lookup.read(equipment))) {
                if (field.tag().equals("layer")) layer = GameScript.integer(field.first(), 0);
                if (!field.tag().equals(action) || field.first().isEmpty()) continue;
                String animation = GameScript.resolve(equipment, field.first(), lookup);
                if (animation != null) layers.add(new SummonLayer(animation, Math.max(-1000, Math.min(1000, layer))));
            }
        }
        return List.copyOf(layers);
    }

    /** State 10 dispatches these through the native summon handler at 0x614740. */
    private static Act1SkillSequence summon(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                            Map<String, String> actions, int level, String choice) throws IOException {
        var values = GameScript.values(fields, "static data");
        if (values.size() < 8) return null;
        int mode = GameScript.integer(values.get(0), -1);
        int group = 1 + GameScript.integer(values.get(3), -1);
        if (mode < 0 || mode > 2 || group < 1 || group > 4 || !hasThrow(actions, group)) return null;
        int monsterId = GameScript.integer(values.get(2), -1);
        String monster = monsterPath(monsterId, lookup);
        if (monster == null) return null;
        var monsterFields = GameScript.fields(lookup.read(monster));
        String motion = GameScript.resolve(monster, GameScript.value(monsterFields, "waiting motion"), lookup);
        if (motion == null) return null;
        String name = switch (id) {
            case 21 -> "亚德炎";
            case 22 -> "冰奈斯";
            case 23 -> "瑟冥特克";
            case 24 -> "雷沃斯";
            case 25 -> "赫德尔";
            case 44 -> "桑德尔";
            case 45 -> "袄索";
            case 46 -> "路易丝";
            default -> "伊伽贝拉";
        };
        long cast = Act1SkillSequence.castingTime(fields, level, 700);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 4, 600));
        int range = Act1SkillSequence.staticValue(fields, 5, 100);
        long idleDuration = animationDuration(lookup, motion);
        if (idleDuration == 0) return null;
        double spawnX = mode == 2 ? range : 100;
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        long idleStart = cast;
        if (monsterId == 901) {
            // 0x6A43B0 starts the wake-up ANI. 0x6A2BB0 enables attachments and the AI after its last frame.
            var extra = GameScript.values(monsterFields, "etc motion");
            if (extra.size() > 4) {
                String wake = GameScript.resolve(monster, extra.get(4), lookup);
                if (wake != null) idleStart = add(lookup, visuals, wake, name + " · 苏醒", 3550, cast, -1, spawnX, 1, 0);
            }
        }
        long attackStart = Math.max(recovery, idleStart);
        long end = attackStart + Math.min(3000, idleDuration);
        var idleEquipment = summonEquipment(monsterId, "waiting motion", lookup);
        var idleStyle = new VisualStyle(null, 0, List.of(), Map.of(), false, idleDuration);
        SummonAttackResult selected = null;
        if (choice.startsWith("mage-contract:")) {
            selected = Act1ContractAttacks.append(monsterId, monster, choice, lookup, attackStart, spawnX, visuals, emitters);
        } else if (choice.isEmpty() || choice.startsWith("mage-spirit:")) {
            selected = Act1SpiritAttacks.append(monsterId, monster, choice, lookup, attackStart, spawnX, visuals);
        }
        if (selected == null && !choice.isEmpty() && !choice.equals(SUMMON_APPEARANCE)) return null;
        var attack = selected == null && choice.isEmpty() ? summonAttack(monsterId, monster, lookup) : null;
        long firstIdleEnd = selected == null && attack == null ? end : attackStart;
        addSummonIdle(visuals, idleEquipment, motion, name + " · 现身", idleStart, firstIdleEnd, spawnX, idleStyle);
        Act1SpiritAttacks.appendIdleEquipment(monsterId, name + " · 现身", lookup, idleStart, firstIdleEnd, spawnX,
                idleStyle, visuals);
        if (selected != null) {
            end = Math.max(end, selected.end());
            if (!selected.despawned()) {
                addSummonIdle(visuals, idleEquipment, motion, name + " · 待机", selected.bodyEnd(), end,
                        selected.bodyX(), idleStyle);
                Act1SpiritAttacks.appendIdleEquipment(monsterId, name + " · 待机", lookup, selected.bodyEnd(), end,
                        selected.bodyX(), idleStyle, visuals);
            }
        } else if (attack != null) {
            long attackEnd = attackStart + attack.duration();
            end = Math.max(end, attackEnd);
            String label = name + " · 普通攻击";
            visuals.add(new Visual(attack.path(), label, 3550, attackStart, attackEnd, spawnX, 0).asAlliedAttack());
            for (var layer : attack.equipment()) {
                visuals.add(new Visual(layer.path(), label, 3550 + layer.order(), attackStart, attackEnd, spawnX, 0)
                        .asAlliedAttack());
            }
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "普通攻击目标 · 哥布林", cast, end,
                    spawnX + attack.targetOffset(), null);
            addSummonIdle(visuals, idleEquipment, motion, name + " · 待机", attackEnd, end, spawnX, idleStyle);
        }
        if (mode < 2) {
            for (int i = 1; i <= 4; i++) {
                end = Math.max(end, add(lookup, visuals, EFFECT + "summonspirit" + i + ".ani", name + " · 召唤阵",
                        280 + i, cast, -1, 0, range / RING_DIAMETER, 0));
            }
        }
        String note = switch (mode) {
            case 0 -> name + "演示没有同属性来源时的召唤落点；附近的同属性来源会改变召唤数量与位置。";
            case 1 -> name + "演示满足献祭条件后的现身；所需精灵数量由技能静态数据配置。";
            default -> name + "演示契约召唤的原版施法和召唤落点。";
        };
        if (selected != null) note += "施法者收招后演示选定的召唤兽 AI 招式。";
        else if (attack != null) note += "施法者收招后演示一次召唤兽对近处目标的普通攻击。";
        else note += "召唤兽随后待机。";
        return sequence(actions, group, cast, recovery, end, visuals, List.copyOf(emitters), note);
    }

    private static void addSummonIdle(List<Visual> visuals, List<SummonLayer> equipment, String motion, String label,
                                      long start, long end, double x, VisualStyle style) {
        if (start >= end) return;
        visuals.add(new Visual(motion, label, 3550, start, end, x, 0, null, 1, true, style).withoutAttack());
        for (var layer : equipment) {
            visuals.add(new Visual(layer.path(), label, 3550 + layer.order(), start, end, x, 0, null, 1, true, style)
                    .withoutAttack());
        }
    }

    private static String monsterIdle(int monsterId, GameScript.Lookup lookup) throws IOException {
        String monster = monsterPath(monsterId, lookup);
        if (monster == null) return null;
        String idle = GameScript.value(GameScript.fields(lookup.read(monster)), "waiting motion");
        return idle.isEmpty() ? null : GameScript.resolve(monster, idle, lookup);
    }

    private static String monsterPath(int monsterId, GameScript.Lookup lookup) throws IOException {
        String reference = GameScript.catalog(lookup.read(MONSTER_LIST)).get(monsterId);
        return reference == null ? null : GameScript.resolve(MONSTER_LIST, reference, lookup);
    }

    /** Disenchant and SunBurst use state 10 and attach authored ground and casting layers. */
    private static Act1SkillSequence centeredCast(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                  Map<String, String> actions, int level, int height) throws IOException {
        boolean dispel = id == 28;
        int group = dispel ? 3 : 4;
        if (!hasThrow(actions, group)) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, dispel ? 1000 : 200);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 500));
        var visuals = new ArrayList<Visual>();
        String name = dispel ? "驱散魔法" : "雷旋";
        String prefix = dispel ? "disenchant" : "sunburst";
        add(lookup, visuals, EFFECT + prefix + "cast1.ani", name + " · 施法后层", 3499, 0, cast, 0, -height, 1, cast);
        long end;
        if (dispel) {
            end = add(lookup, visuals, EFFECT + prefix + "cast2.ani", name + " · 施放", 3501, cast, -1, 0, 1, 0);
            double scale = Act1SkillSequence.levelValue(fields, level, 0, 300, 1, 10000) / RING_DIAMETER;
            for (int i = 1; i <= 4; i++) {
                end = Math.max(end, add(lookup, visuals, EFFECT + "disenchantarea" + i + ".ani",
                        name + " · 驱散范围", 280 + i, cast, -1, 0, scale, 0));
            }
        } else {
            add(lookup, visuals, EFFECT + "sunburstcast2.ani", name + " · 施法前层", 3501, 0, cast, 0, -height, 1, cast);
            add(lookup, visuals, EFFECT + "sunburstfloor1.ani", name + " · 蓄电", 280, 0, cast, 0, 1, cast);
            end = add(lookup, visuals, EFFECT + "sunburstfloor2.ani", name + " · 地面放电", 280, cast, -1, 0, 1, 0);
            if (GameEffectRecipes.passiveObject(lookup, 23015) != null) {
                double scale = Act1SkillSequence.levelValue(fields, level, 2, 1000, 1, 10000) / 1000.0;
                var motions = Act1SkillSequence.objectMotions(lookup, 23015);
                if (!motions.isEmpty()) end = Math.max(end, add(lookup, visuals, motions.get(0),
                        name + " · 攻击判定", 3600, cast, -1, 0, -height, scale, 0));
                for (int index = 1; index <= 5; index++) {
                    String path = PASSIVE + "animation/sunburstlayer" + index + ".ani";
                    end = Math.max(end, add(lookup, visuals, path, name + " · 放电", 3600 + index,
                            cast, -1, 0, -height, scale, 0));
                }
            }
        }
        if (visuals.isEmpty()) return null;
        String note = dispel ? "驱散魔法按技能等级显示驱散范围；驱散对象取决于范围内的有效状态"
                : "雷旋演示短按的蓄电与放电；满蓄变化和命中反馈取决于游戏输入和目标";
        return sequence(actions, group, cast, recovery, end, visuals, List.of(), note);
    }

    private static long add(GameScript.Lookup lookup, List<Visual> visuals, String path, String label, int layer,
                            long start, long end, double x, double scale, long fit) throws IOException {
        return add(lookup, visuals, path, label, layer, start, end, x, 0, scale, fit);
    }

    private static long add(GameScript.Lookup lookup, List<Visual> visuals, String path, String label, int layer,
                            long start, long end, double x, double y, double scale, long fit) throws IOException {
        long duration = animationDuration(lookup, path);
        if (duration == 0 || (end >= 0 && end <= start)) return start;
        long finish = end >= 0 ? end : start + duration;
        var style = fit == 0 ? VisualStyle.DEFAULT : new VisualStyle(null, fit, List.of());
        visuals.add(new Visual(path, label, layer, start, finish, x, y, null, scale, true, style));
        return finish;
    }

    private static long animationDuration(GameScript.Lookup lookup, String path) throws IOException {
        String text = lookup.read(path);
        return text == null ? 0 : Act1SkillSequence.duration(ResourceScript.animation(text).frames(), Integer.MAX_VALUE, null);
    }

    private static boolean hasThrow(Map<String, String> actions, int group) {
        return actions.containsKey("throw motion " + group + "-1") && actions.containsKey("throw motion " + group + "-2");
    }

    private static Act1SkillSequence sequence(Map<String, String> actions, int group, long cast, long recovery,
                                             long finish, List<Visual> visuals, List<SkillParticleEmitter> emitters,
                                             String note) {
        String prefix = "throw motion " + group;
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions, prefix + "-1", prefix + "-2", cast, recovery,
                finish, false), List.copyOf(visuals), emitters, note);
    }
}
