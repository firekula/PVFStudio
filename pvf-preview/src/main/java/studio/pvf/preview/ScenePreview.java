package studio.pvf.preview;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;

/** A resolved scene holds configurations, while each tick loads only the currently visible animation frames. */
public final class ScenePreview {
    public static final Color BLOCKED = new Color(245, 70, 78, 95), FLOAT_ONLY = new Color(255, 170, 35, 110);
    private static final long CACHE_LIMIT = 32L * 1024 * 1024, FRAME_LIMIT = 96L * 1024 * 1024;
    private final List<Track> tracks = new ArrayList<>();
    private final List<Collision> collisions = new ArrayList<>();
    private final List<EntityInfo> entities = new ArrayList<>();
    private final List<Emitter> emitters = new ArrayList<>();
    private final List<ObjectCollision> objectCollisions = new ArrayList<>();
    private final Map<String, Double> scrolls = new HashMap<>();
    private final Map<EntityScript.Kind, Map<Integer, String>> catalogs = new EnumMap<>(EntityScript.Kind.class);
    private final Map<EntityScript.Kind, String> catalogErrors = new EnumMap<>(EntityScript.Kind.class);
    private final Map<String, EntityScript.Definition> definitions = new HashMap<>();
    private final LinkedHashSet<String> warnings = new LinkedHashSet<>();
    private final Map<String, Resolved> scripts = new HashMap<>();
    private final LinkedHashMap<ResourceScript.Frame, PreviewRenderer.Prepared> cache = new LinkedHashMap<>(32, .75f, true);
    private final Map<ResourceScript.Frame, String> failedImages = new HashMap<>();
    private final TextLookup lookup;
    private final SpriteLookup sprites;
    private final int[] variants;
    private final boolean expandBounds;
    private final List<Rectangle2D.Double> movable;
    private Rectangle2D bounds;
    private long cacheBytes;
    private int tileCount;
    private int particleDefinitions;

    private ScenePreview(TextLookup lookup, SpriteLookup sprites, int[] variants, Rectangle2D bounds,
                         List<Rectangle2D.Double> movable, boolean expandBounds) {
        this.lookup = lookup; this.sprites = sprites; this.variants = variants.clone(); this.bounds = bounds; this.movable = movable;
        this.expandBounds = expandBounds;
    }

    public static ScenePreview load(String path, String text, TextLookup lookup, SpriteLookup sprites, int... variants) throws IOException {
        if (path.toLowerCase(Locale.ROOT).endsWith(".ptl")) {
            ParticleScript script = ParticleScript.parse(text); var p = script.position();
            double width = Math.max(800, Math.min(4096, script.area().x() + 400));
            double height = Math.max(600, Math.min(4096, script.area().y() * .29 + script.area().z() + 400));
            ScenePreview scene = new ScenePreview(lookup, sprites, variants,
                    new Rectangle2D.Double(p.x() - width / 2, SceneCoordinates.screenY(p.y(), p.z()) - height / 2, width, height), List.of(), false);
            scene.scripts.put(path + "\0" + path, new Resolved(path, text));
            EmitterVisual visual = scene.loadEmitter(new Resolved(path, text), 0, false, new HashSet<>());
            scene.emitters.add(new Emitter(visual, p.x(), p.y(), p.z(), false, -1, path.hashCode()));
            return scene;
        }
        boolean tile = path.toLowerCase(Locale.ROOT).endsWith(".til");
        SceneScript.MapLayout map = tile ? null : SceneScript.map(text);
        ScenePreview scene = new ScenePreview(lookup, sprites, variants,
                new Rectangle2D.Double(0, 0, tile ? 224 : map.tiles().size() * 224.0,
                        tile ? SceneScript.tile(text).height() : 480),
                tile ? List.of() : map.movable().stream().map(area -> new Rectangle2D.Double(area.x,
                        SceneCoordinates.screenY(area.y, 0), area.width, area.height)).toList(), tile);
        if (tile) {
            var parsed = SceneScript.tile(text);
            scene.addTile(path, parsed, 0, 0, parsed.extended() ? 0 : SceneCoordinates.ORIGIN_Y);
        }
        else {
            scene.scrolls.put("distantback", map.farScroll()); scene.scrolls.put("middleback", map.middleScroll()); scene.scrolls.put("close", map.nearScroll());
            double baseHeight = 480;
            for (int i = 0; i < map.tiles().size(); i++) {
                var parsed = scene.loadTile(path, map.tiles().get(i), i * 224.0, 0, SceneCoordinates.ORIGIN_Y);
                if (i == 0 && parsed != null) baseHeight = parsed.worldHeight() + SceneCoordinates.ORIGIN_Y;
            }
            int extendedRows = map.extended().size() / map.tiles().size();
            if (map.extended().size() % map.tiles().size() != 0) scene.warn("扩展地块末行不完整，按客户端规则只显示完整行");
            for (int i = 0; i < extendedRows * map.tiles().size(); i++) {
                double y = baseHeight + (i / map.tiles().size()) * SceneCoordinates.EXTENDED_HEIGHT;
                scene.loadTile(path, map.extended().get(i), (i % map.tiles().size()) * 224.0, y, y);
            }
            scene.bounds = new Rectangle2D.Double(0, 0, map.tiles().size() * 224.0,
                    baseHeight + extendedRows * SceneCoordinates.EXTENDED_HEIGHT);
            for (SceneScript.Placement placement : map.animations()) {
                try {
                    Resolved resolved = scene.resolve(path, placement.path());
                    ResourceScript.Animation animation = ResourceScript.animation(resolved.text());
                    double scroll = switch (placement.layer()) {
                        case "distantback" -> map.farScroll();
                        case "middleback" -> map.middleScroll();
                        case "close" -> map.nearScroll();
                        default -> 1;
                    };
                    scene.addTrack(resolved.path(), animation, placement.x(), placement.repeat() ? 0 : SceneCoordinates.screenY(placement.y(), placement.z()),
                            SceneScript.stage(placement), placement.y(), scroll, placement.repeat());
                } catch (IOException failure) { scene.warn(placement.path() + "：" + failure.getMessage()); }
            }
            EntityScript.Layout layout = EntityScript.placements(text);
            layout.warnings().forEach(scene::warn);
            for (EntityScript.Spawn spawn : layout.spawns()) scene.addEntity(spawn);
        }
        scene.tracks.sort(Comparator.comparingInt(Track::stage).thenComparingDouble(Track::depth).thenComparingInt(Track::order));
        return scene;
    }

    private SceneScript.Tile loadTile(String source, String reference, double x, double y, double collisionY) {
        try {
            Resolved resolved = resolve(source, reference); var tile = SceneScript.tile(resolved.text());
            addTile(resolved.path(), tile, x, y, collisionY); return tile;
        } catch (IOException failure) { warn(reference + "：" + failure.getMessage()); return null; }
    }
    private void addTile(String path, SceneScript.Tile tile, double x, double y, double collisionY) {
        tileCount++; tile.warnings().forEach(warning -> warn(path + "：" + warning));
        if (!tile.passes().isEmpty()) collisions.add(new Collision(path, x, collisionY, tile));
        for (ResourceScript.Frame image : tile.images()) addTrack(path,
                new ResourceScript.Animation(false, List.of(image), List.of()), x, y, 10, 0, 1, false);
    }
    private void addTrack(String path, ResourceScript.Animation animation, double x, double y, int stage,
                          double depth, double scroll, boolean repeat) {
        addTrack(path, animation, x, y, stage, depth, scroll, repeat, false, -1);
    }
    private void addTrack(String path, ResourceScript.Animation animation, double x, double y, int stage,
                          double depth, double scroll, boolean repeat, boolean mirrored, int entity) {
        if (tracks.size() >= 8192) { warn("场景超过 8192 个图层，后续图层未加载"); return; }
        animation.warnings().forEach(warning -> warn(path + "：" + warning));
        tracks.add(new Track(path, animation, new AnimationTimeline(animation), x, y, stage, depth, scroll, repeat, tracks.size(), mirrored, entity));
        // MAP extent comes from its tile rows: decorative/backdrop ANI may intentionally extend far outside it.
        if (!repeat && expandBounds) for (ResourceScript.Frame frame : animation.frames()) {
            if (frame.image().isEmpty()) continue;
            // Union every frame's visible native rectangle once. Large empty IMG margins must not expand a map.
            try {
                NpkResources.Info info = sprites.info(NpkResources.substitute(frame.image(), variants), frame.index());
                AffineTransform transform = PreviewRenderer.transform(frame, info.canvasWidth(), info.canvasHeight(), info.x(), info.y());
                transform.preConcatenate(AffineTransform.getTranslateInstance(x, y));
                bounds = bounds.createUnion(transform.createTransformedShape(new Rectangle2D.Double(0, 0,
                        Math.max(1, info.width()), Math.max(1, info.height()))).getBounds2D());
            } catch (IOException failure) { warn(path + "：" + failure.getMessage()); }
        }
    }

    private void addEntity(EntityScript.Spawn spawn) {
        int index = entities.size(); String definitionPath = "", problem = "";
        List<IdlePart> parts = new ArrayList<>();
        try {
            var kind = spawn.kind();
            if (catalogErrors.containsKey(kind)) throw new IOException(catalogErrors.get(kind));
            Map<Integer, String> catalog = catalogs.get(kind);
            if (catalog == null) {
                try {
                    String list = lookup.read(kind.list());
                    if (list == null) throw new IOException("找不到索引列表 " + kind.list());
                    catalog = EntityScript.catalog(list); catalogs.put(kind, catalog);
                } catch (IOException failure) { catalogErrors.put(kind, failure.getMessage()); throw failure; }
            }
            String reference = catalog.get(spawn.id());
            if (reference == null) throw new IOException(kind.list() + " 没有编号 " + spawn.id());
            Resolved resolved = resolve(kind.list(), reference); definitionPath = resolved.path();
            EntityScript.Definition definition = definitions.get(definitionPath);
            if (definition == null) { definition = EntityScript.definition(resolved.text(), kind); definitions.put(definitionPath, definition); }
            boolean particle = false;
            if (kind == EntityScript.Kind.PASSIVE) {
                if (definition.pass() < 0) warn(spawn.label() + " " + definitionPath + "：[pass type] 无法识别，保留图像并省略碰撞范围");
                if (definition.pass() > 0 && definition.width() > 0 && definition.depth() > 0)
                    objectCollisions.add(new ObjectCollision(index, definitionPath, spawn, definition.width(), definition.depth(), definition.pass()));
                EntityScript.Factory factory = EntityScript.particleFactory(resolved.text(), spawn.id());
                if (factory != null) {
                    Resolved ptl = resolve(definitionPath, factory.path());
                    EmitterVisual visual = loadEmitter(ptl, factory.initialTime(), factory.fillMap(), new HashSet<>());
                    emitters.add(new Emitter(visual, factory.fillMap() ? bounds.getWidth() / 2 : spawn.x(), spawn.y(), spawn.z(),
                            spawn.mirrored(), index, 31L * definitionPath.hashCode() + index));
                    particle = true;
                }
            }
            if (definition.idle().isEmpty() && !particle) problem = "没有 [" + kind.motion() + "] 待机动画（可能为逻辑对象）";
            else addIdlePart(parts, definitionPath, definition.idle(), 0);
            Map<String, Gear> worn = new LinkedHashMap<>();
            List<NativeEquipmentRecipes.Equipment> equipmentList = new ArrayList<>();
            definition.equipment().forEach(path -> equipmentList.add(new NativeEquipmentRecipes.Equipment(path, List.of())));
            if (kind == EntityScript.Kind.MONSTER) equipmentList.addAll(NativeEquipmentRecipes.forMonster(spawn.id()));
            for (var equipment : equipmentList) {
                try {
                    Resolved gear = resolveEquipment(definitionPath, equipment.path());
                    EntityScript.Part part = EntityScript.equipment(gear.text());
                    // The native equip operation replaces the previous item in the same slot (e.g. bronze over steel).
                    worn.put(part.slot().isEmpty() ? gear.path() : part.slot(), new Gear(gear.path(), part,
                            equipment.variation().isEmpty() ? part.variation() : equipment.variation()));
                } catch (IOException failure) { warn(spawn.label() + " 装备 " + equipment.path() + "：" + failure.getMessage()); problem = "部分装备未加载"; }
            }
            Set<Integer> hidden = new HashSet<>(); worn.values().forEach(gear -> hidden.addAll(gear.part().hidden()));
            parts.removeIf(part -> hidden.contains(part.layer()));
            for (Gear gear : worn.values()) {
                try {
                    for (var motion : gear.part().motions()) if (!motion.path().isEmpty() && !hidden.contains(motion.layer()))
                        addIdlePart(parts, gear.path(), motion.path(), motion.layer(), gear.variation().stream().mapToInt(Integer::intValue).toArray());
                } catch (IOException failure) { warn(spawn.label() + " 装备待机 " + gear.path() + "：" + failure.getMessage()); problem = "部分装备未加载"; }
            }
            for (int i = 0; i < definition.effects().size(); i += 2) {
                try { addIdlePart(parts, definitionPath, definition.effects().get(i), EntityScript.integer(definition.effects(), i + 1)); }
                catch (IOException failure) { warn(spawn.label() + " 附加动画：" + failure.getMessage()); problem = "部分附加动画未加载"; }
            }
            parts.sort(Comparator.comparingInt(IdlePart::layer));
            int stage = SceneScript.stage(new SceneScript.Placement("", definition.layer(), false, false, 0, 0, 0));
            for (IdlePart part : parts) addTrack(part.path(), part.animation(), spawn.x(), SceneCoordinates.screenY(spawn.y(), spawn.z()), stage,
                    spawn.y(), 1, false, spawn.mirrored(), index);
            if (parts.isEmpty() && !particle && problem.isEmpty()) problem = "待机动画为空";
        } catch (IOException failure) { problem = failure.getMessage(); }
        if (!problem.isEmpty()) warn(spawn.label() + "：" + problem);
        entities.add(new EntityInfo(spawn, definitionPath, parts.stream().map(IdlePart::path).toList(), problem));
    }
    private void addIdlePart(List<IdlePart> parts, String source, String reference, int layer) throws IOException {
        addIdlePart(parts, source, reference, layer, variants);
    }
    private void addIdlePart(List<IdlePart> parts, String source, String reference, int layer, int[] variation) throws IOException {
        if (reference.isEmpty()) return;
        Resolved idle = resolve(source, reference); ResourceScript.Animation animation = ResourceScript.animation(idle.text());
        List<ResourceScript.Frame> frames = new ArrayList<>();
        for (var frame : animation.frames()) frames.add(frame.withImage(NpkResources.substitute(frame.image(), variation)));
        animation = new ResourceScript.Animation(animation.loop(), List.copyOf(frames), animation.warnings());
        if (!animation.frames().isEmpty()) parts.add(new IdlePart(idle.path(), animation, layer));
    }
    private Resolved resolveEquipment(String source, String reference) throws IOException {
        String path = reference.replace('\\', '/').toLowerCase(Locale.ROOT);
        String root = path.startsWith("equipment/") ? path : "equipment/" + path;
        String text = lookup.read(root);
        return text == null ? resolve(source, reference) : new Resolved(root, text);
    }

    private EmitterVisual loadEmitter(Resolved ptl, long initial, boolean fillMap, Set<String> ancestors) throws IOException {
        if (ancestors.size() >= 4 || !ancestors.add(ptl.path())) throw new IOException("粒子生成器循环引用或嵌套超过 4 层：" + ptl.path());
        if (++particleDefinitions > 512) throw new IOException("粒子生成器配置超过 512 个");
        ParticleScript script = ParticleScript.parse(ptl.text());
        if (fillMap) script = script.fillWidth(bounds.getWidth());
        script.warnings().forEach(message -> warn(ptl.path() + "：" + message));
        List<ParticleVisual> visuals = new ArrayList<>();
        for (var object : script.objects()) {
            List<IdlePart> parts = new ArrayList<>(); String layer = script.layer(); EmitterVisual child = null;
            try {
                if (!script.passive()) {
                    if (object.index() == -1) addIdlePart(parts, ptl.path(), object.path(), 0);
                    else if (object.index() >= 0 && object.path().toLowerCase(Locale.ROOT).endsWith(".img"))
                        parts.add(new IdlePart(object.path(), new ResourceScript.Animation(true,
                                List.of(ResourceScript.Frame.still(object.path(), object.index())), List.of()), 0));
                    else throw new IOException("IMG 帧号或 ANI 标记无效");
                } else {
                    var kind = EntityScript.Kind.PASSIVE;
                    Map<Integer, String> catalog = catalogs.get(kind);
                    if (catalog == null) {
                        String list = lookup.read(kind.list());
                        if (list == null) throw new IOException("找不到 " + kind.list());
                        catalog = EntityScript.catalog(list); catalogs.put(kind, catalog);
                    }
                    String reference = catalog.get(object.index());
                    if (reference == null) throw new IOException("找不到被动对象 #" + object.index());
                    Resolved definition = resolve(kind.list(), reference);
                    var parsed = EntityScript.definition(definition.text(), kind); layer = parsed.layer();
                    if (!parsed.idle().isEmpty()) addIdlePart(parts, definition.path(), parsed.idle(), 0);
                    for (int i = 0; i < parsed.effects().size(); i += 2)
                        addIdlePart(parts, definition.path(), parsed.effects().get(i), EntityScript.integer(parsed.effects(), i + 1));
                    var factory = EntityScript.particleFactory(definition.text(), object.index());
                    if (factory != null) child = loadEmitter(resolve(definition.path(), factory.path()), factory.initialTime(), false, new HashSet<>(ancestors));
                    if (parts.isEmpty() && child == null) throw new IOException("被动粒子没有初始视觉定义 #" + object.index());
                }
            } catch (IOException failure) { warn(ptl.path() + " 粒子对象 " + object.path() + " #" + object.index() + "：" + failure.getMessage()); }
            parts.sort(Comparator.comparingInt(IdlePart::layer));
            for (var part : parts) part.animation().warnings().forEach(message -> warn(part.path() + "：" + message));
            visuals.add(new ParticleVisual(List.copyOf(parts), parts.stream().map(part -> new AnimationTimeline(part.animation())).toList(),
                    SceneScript.stage(new SceneScript.Placement("", layer, false, false, 0, 0, 0)), scrolls.getOrDefault(layer, 1.0), child));
        }
        if (script.objects().isEmpty()) warn(ptl.path() + "：没有粒子对象");
        return new EmitterVisual(ptl.path(), script, initial, List.copyOf(visuals));
    }

    private Resolved resolve(String source, String reference) throws IOException {
        String key = source + "\0" + reference;
        Resolved cached = scripts.get(key); if (cached != null) return cached;
        for (String candidate : ResourceScript.animationCandidates(source, reference)) {
            String text = lookup.read(candidate);
            if (text != null) { Resolved resolved = new Resolved(candidate, text); scripts.put(key, resolved); return resolved; }
        }
        throw new IOException("找不到配置，已尝试 " + String.join("、", ResourceScript.animationCandidates(source, reference)));
    }

    /** Called only by the preview worker. Absolute elapsed time independently samples every ANI. */
    public Rendered render(long milliseconds, boolean forceLoop) throws IOException {
        return render(milliseconds, forceLoop, false);
    }
    public Rendered render(long milliseconds, boolean forceLoop, boolean includeConditional) throws IOException {
        List<Draw> draws = new ArrayList<>(); Map<String, Integer> active = new LinkedHashMap<>();
        Map<Integer, Rectangle2D> entityBounds = new HashMap<>(); Map<Integer, String> entityErrors = new HashMap<>();
        Set<BufferedImage> images = Collections.newSetFromMap(new IdentityHashMap<>()); long imageBytes = 0;
        for (Track track : tracks) {
            NpkResources.checkInterrupted();
            if (track.entity() >= 0 && entities.get(track.entity()).spawn().conditional() && !includeConditional) continue;
            int index = track.timeline().frameAt(milliseconds, forceLoop);
            if (index < 0) continue;
            ResourceScript.Frame frame = track.timeline().sample(milliseconds, forceLoop);
            active.put(track.order() + ":" + track.path(), index);
            if (frame.image().isEmpty()) continue;
            try {
                PreviewRenderer.Prepared prepared = prepare(frame);
                if (images.add(prepared.image())) imageBytes += bytes(prepared);
                if (imageBytes > FRAME_LIMIT) { warn("当前场景像素超过 96 MiB，部分图层未显示"); continue; }
                draws.add(new Draw(prepared, track.x(), track.y(), track.scroll(), track.repeat(), frame.scaleX(), frame.scaleY(),
                        track.mirrored(), 1, track.stage(), track.depth(), track.order()));
                if (track.entity() >= 0) {
                    Rectangle2D b = prepared.bounds(track.mirrored());
                    Rectangle2D world = new Rectangle2D.Double(track.x() + b.getX(),
                            track.y() + b.getY(), b.getWidth(), b.getHeight());
                    entityBounds.merge(track.entity(), world, Rectangle2D::createUnion);
                }
            } catch (IOException failure) {
                warn(track.path() + "：" + failure.getMessage());
                if (track.entity() >= 0) entityErrors.put(track.entity(), failure.getMessage());
            }
        }
        ParticleRender particles = new ParticleRender(draws, entityBounds, images, imageBytes);
        for (Emitter emitter : emitters) {
            if (emitter.entity() >= 0 && entities.get(emitter.entity()).spawn().conditional() && !includeConditional) continue;
            drawEmitter(emitter.visual(), milliseconds, emitter.x(), emitter.y(), emitter.z(), emitter.mirrored(), emitter.entity(),
                    emitter.seed(), 1, forceLoop, particles);
        }
        draws.sort(Comparator.comparingInt(Draw::stage).thenComparingDouble(Draw::depth).thenComparingInt(Draw::order));
        List<EntityView> views = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            EntityInfo info = entities.get(i); var spawn = info.spawn();
            if (spawn.conditional() && !includeConditional) continue;
            String problem = entityErrors.getOrDefault(i, info.problem());
            Rectangle2D area = entityBounds.getOrDefault(i, new Rectangle2D.Double(spawn.x() - 8, SceneCoordinates.screenY(spawn.y(), spawn.z()) - 8, 16, 16));
            views.add(new EntityView(info, area, problem));
        }
        return new Rendered(List.copyOf(draws), List.copyOf(collisions), movable, bounds, milliseconds,
                Map.copyOf(active), List.copyOf(warnings), tileCount, tracks.size(), List.copyOf(views),
                (int) entities.stream().filter(entity -> entity.spawn().conditional()).count(), List.copyOf(objectCollisions), emitters.size(), particles.count);
    }

    private final class ParticleRender {
        final List<Draw> draws; final Map<Integer, Rectangle2D> entityBounds; final Set<BufferedImage> images;
        long bytes; int count, order = tracks.size();
        ParticleRender(List<Draw> draws, Map<Integer, Rectangle2D> entityBounds, Set<BufferedImage> images, long bytes) {
            this.draws = draws; this.entityBounds = entityBounds; this.images = images; this.bytes = bytes;
        }
    }
    private void drawEmitter(EmitterVisual emitter, long time, double x, double y, double z, boolean mirrored, int entity,
                             long seed, double opacity, boolean forceLoop, ParticleRender state) throws IOException {
        if (state.count >= 8192) { warn("场景超过 8192 个活动粒子，后续未显示"); return; }
        var sampled = new ParticleTimeline(emitter.script(), seed, emitter.initial()).sample(time, x, y, z, mirrored);
        if (sampled.limited()) warn(emitter.path() + "：同时存活的粒子超过 1024 个，显示最近 1024 个");
        for (var particle : sampled.particles()) {
            NpkResources.checkInterrupted();
            if (state.count >= 8192) { warn("场景超过 8192 个活动粒子，后续未显示"); break; }
            state.count++;
            var visual = emitter.visuals().get(particle.object());
            double screenY = SceneCoordinates.screenY(particle.y(), particle.z()), alpha = opacity * particle.alpha();
            for (int i = 0; i < visual.parts().size(); i++) {
                var part = visual.parts().get(i); int index = visual.timelines().get(i).frameAt(particle.age(), forceLoop);
                if (index < 0) continue; var frame = visual.timelines().get(i).sample(particle.age(), forceLoop);
                if (frame.image().isEmpty() || alpha <= 0) continue;
                try {
                    var prepared = prepare(frame);
                    if (state.images.add(prepared.image())) state.bytes += bytes(prepared);
                    if (state.bytes > FRAME_LIMIT) { warn("当前场景像素超过 96 MiB，部分粒子未显示"); continue; }
                    state.draws.add(new Draw(prepared, particle.x(), screenY, visual.scroll(), false, frame.scaleX(), frame.scaleY(),
                            mirrored, alpha, visual.stage(), particle.y(), state.order++));
                    if (entity >= 0) {
                        var b = prepared.bounds(mirrored);
                        state.entityBounds.merge(entity, new Rectangle2D.Double(particle.x() + b.getX(), screenY + b.getY(), b.getWidth(), b.getHeight()), Rectangle2D::createUnion);
                    }
                } catch (IOException failure) { warn(emitter.path() + " / " + part.path() + "：" + failure.getMessage()); }
            }
            if (visual.child() != null) drawEmitter(visual.child(), particle.age(), particle.x(), particle.y(), particle.z(), mirrored, entity,
                    seed * 31 + particle.sequence(), alpha, forceLoop, state);
        }
    }
    private PreviewRenderer.Prepared prepare(ResourceScript.Frame frame) throws IOException {
        var pixelKey = new ResourceScript.Frame(frame.image(), frame.index(), 0, 0, 1, 1, 0,
                frame.r(), frame.g(), frame.b(), frame.a(), 0, 0, frame.effect());
        return preparePixels(pixelKey).positioned(frame);
    }
    private PreviewRenderer.Prepared preparePixels(ResourceScript.Frame frame) throws IOException {
        if (failedImages.containsKey(frame)) throw new IOException(failedImages.get(frame));
        PreviewRenderer.Prepared prepared = cache.get(frame); if (prepared != null) return prepared;
        try { prepared = PreviewRenderer.prepare(frame, sprites.sprite(NpkResources.substitute(frame.image(), variants), frame.index())); }
        catch (IOException failure) { failedImages.put(frame, failure.getMessage()); throw failure; }
        long bytes = bytes(prepared);
        while (!cache.isEmpty() && (cacheBytes + bytes > CACHE_LIMIT || cache.size() >= 128)) {
            cacheBytes -= bytes(cache.remove(cache.keySet().iterator().next()));
        }
        if (bytes <= CACHE_LIMIT) { cache.put(frame, prepared); cacheBytes += bytes; }
        return prepared;
    }
    private static long bytes(PreviewRenderer.Prepared prepared) { return (long) prepared.image().getWidth() * prepared.image().getHeight() * 4; }
    private void warn(String message) { if (warnings.size() < 256) warnings.add(message); }

    public record Rendered(List<Draw> draws, List<Collision> collisions, List<Rectangle2D.Double> movable,
                           Rectangle2D bounds, long milliseconds, Map<String, Integer> activeFrames,
                           List<String> warnings, int tileCount, int trackCount, List<EntityView> entities, int conditionalCount,
                           List<ObjectCollision> objectCollisions, int emitterCount, int particleCount) {
        public void paint(Graphics2D graphics, Rectangle viewport, double ox, double oy, double zoom,
                          double panX, double panY, boolean overlay) {
            paint(graphics, viewport, ox, oy, zoom, panX, panY, overlay, false);
        }
        public void paint(Graphics2D graphics, Rectangle viewport, double ox, double oy, double zoom,
                          double panX, double panY, boolean overlay, boolean markers) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                Rectangle2D area = new Rectangle2D.Double(ox + panX + bounds.getX() * zoom,
                        oy + panY + bounds.getY() * zoom, bounds.getWidth() * zoom, bounds.getHeight() * zoom);
                g.clip(area); Rectangle visible = viewport.intersection(area.getBounds());
                for (Draw draw : draws) {
                    double x = ox + panX * draw.scroll(), y = oy + panY * draw.scroll();
                    if (!draw.repeat()) draw.prepared().paint(g, x + draw.x() * zoom, y + draw.y() * zoom, zoom, draw.mirrored(), draw.opacity());
                    else paintRepeated(g, visible, draw, x, y, zoom);
                }
                if (overlay) paintCollisions(g, visible, ox + panX, oy + panY, zoom);
                paintEntities(g, viewport, ox + panX, oy + panY, zoom, markers);
            } finally { g.dispose(); }
        }
        private void paintEntities(Graphics2D g, Rectangle viewport, double ox, double oy, double zoom, boolean markers) {
            for (EntityView view : entities) {
                if (!markers && view.problem().isEmpty()) continue;
                var spawn = view.info().spawn(); int x = (int) Math.round(ox + spawn.x() * zoom);
                int y = (int) Math.round(oy + SceneCoordinates.screenY(spawn.y(), spawn.z()) * zoom);
                g.setColor(view.problem().isEmpty() ? new Color(90, 230, 240) : new Color(255, 185, 65));
                g.drawLine(x - 4, y, x + 4, y); g.drawLine(x, y - 4, x, y + 4);
                g.drawString(spawn.label(), x + 6, y < viewport.y + 15 ? y + 14 : y - 5);
            }
        }
        public String entityAt(double x, double y) {
            for (int i = entities.size() - 1; i >= 0; i--) {
                EntityView view = entities.get(i); if (!view.bounds().contains(x, y)) continue;
                var spawn = view.info().spawn();
                return spawn.label() + " · " + (spawn.mirrored() ? "左向" : "右向") + " · "
                        + spawn.x() + "," + spawn.y() + "," + spawn.z() + " · " + view.info().definition()
                        + (spawn.conditional() ? " · 条件放置（仅预览）" : "") + (view.problem().isEmpty() ? "" : " · " + view.problem());
            }
            return "";
        }
        public void paintCollisions(Graphics2D graphics, Rectangle viewport, double ox, double oy, double zoom) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(ox, oy); g.scale(zoom, zoom);
                Rectangle2D visible = new Rectangle2D.Double((viewport.x - ox) / zoom, (viewport.y - oy) / zoom,
                        viewport.width / zoom, viewport.height / zoom);
                for (Collision collision : collisions) {
                    for (int row = 0; row < collision.tile().rows(); row++) for (int col = 0; col < SceneScript.COLUMNS; col++) {
                        int type = collision.tile().pass(col, row); if (type == 0) continue;
                        Rectangle2D cell = new Rectangle2D.Double(collision.x() + col * SceneCoordinates.CELL_WIDTH,
                                collision.y() + row * SceneCoordinates.CELL_HEIGHT, SceneCoordinates.CELL_WIDTH, SceneCoordinates.CELL_HEIGHT);
                        if (!visible.intersects(cell)) continue;
                        g.setColor(type == 1 ? FLOAT_ONLY : BLOCKED); g.fill(cell);
                        if (zoom >= .7) { g.setColor(type == 1 ? new Color(255, 185, 60, 170) : new Color(255, 100, 100, 130)); g.draw(cell); }
                    }
                }
                g.setColor(new Color(70, 225, 240)); g.setStroke(new BasicStroke((float) (1.5 / zoom)));
                for (Rectangle2D rectangle : movable) g.draw(rectangle);
                Set<EntityScript.Spawn> visibleSpawns = new HashSet<>(); entities.forEach(view -> visibleSpawns.add(view.info().spawn()));
                for (var object : objectCollisions) {
                    if (!visibleSpawns.contains(object.spawn())) continue;
                    var footprint = object.bounds(); g.setColor(object.pass() == 1 ? FLOAT_ONLY : BLOCKED); g.fill(footprint);
                    g.setColor(object.pass() == 1 ? new Color(255, 185, 60) : new Color(255, 90, 100)); g.draw(footprint);
                    g.drawString("#" + object.spawn().id(), (float) footprint.x, (float) footprint.y - 2);
                }
            } finally { g.dispose(); }
        }
        public String collisionAt(double x, double y) {
            String object = objectCollisions.stream().filter(c -> c.bounds().contains(x, y)
                    && entities.stream().anyMatch(view -> view.info().spawn() == c.spawn())).map(c -> c.spawn().label() + " · " + c.path()
                    + " · 碰撞范围 " + c.width() + "×" + c.depth() + " · " + (c.pass() == 1 ? "仅浮空可通行" : "不可通行")).findFirst().orElse("");
            if (!object.isEmpty()) return object;
            if (movable.stream().anyMatch(area -> area.contains(x, y))) return "城镇可移动区域 · [virtual movable area]（蓝框）";
            for (Collision collision : collisions) {
                int column = (int) Math.floor((x - collision.x()) / SceneCoordinates.CELL_WIDTH),
                        row = (int) Math.floor((y - collision.y()) / SceneCoordinates.CELL_HEIGHT);
                if (column >= 0 && column < 14 && row >= 0 && row < collision.tile().rows()) {
                    int type = collision.tile().pass(column, row);
                    return collision.path() + " · 格 " + column + "," + row + " · " + type + "："
                            + new String[]{"可通行", "仅浮空单位可通行", "不可通行"}[type];
                }
            }
            return "此处没有已配置的 TIL 通行格";
        }
    }
    private static void paintRepeated(Graphics2D g, Rectangle viewport, Draw draw, double ox, double oy, double zoom) {
        var prepared = draw.prepared(); var bounds = prepared.bounds();
        double width = prepared.source().canvasWidth() * Math.abs(draw.scaleX());
        if (width <= 0 || bounds.getWidth() == 0 || bounds.getHeight() == 0) return;
        double left = (viewport.x - ox) / zoom, right = left + viewport.width / zoom;
        long startX = (long) Math.floor((left - bounds.getMaxX()) / width) + 1;
        long endX = (long) Math.ceil((right - bounds.getMinX()) / width) - 1;
        int count = 0;
        for (long x = startX; x <= endX; x++) {
            if (++count > 8192) return;
            prepared.paint(g, ox + x * width * zoom, oy, zoom);
        }
    }
    public record Collision(String path, double x, double y, SceneScript.Tile tile) {}
    public record ObjectCollision(int entity, String path, EntityScript.Spawn spawn, double width, double depth, int pass) {
        public Rectangle2D.Double bounds() { return new Rectangle2D.Double(spawn.x() - width / 2, SceneCoordinates.screenY(spawn.y(), 0) - depth / 2, width, depth); }
    }
    public record Draw(PreviewRenderer.Prepared prepared, double x, double y, double scroll,
                       boolean repeat, double scaleX, double scaleY, boolean mirrored, double opacity, int stage, double depth, int order) {
        public Draw(PreviewRenderer.Prepared prepared, double x, double y, double scroll, boolean repeat, double scaleX, double scaleY, boolean mirrored) {
            this(prepared, x, y, scroll, repeat, scaleX, scaleY, mirrored, 1, 0, 0, 0);
        }
    }
    public record EntityInfo(EntityScript.Spawn spawn, String definition, List<String> animations, String problem) {}
    public record EntityView(EntityInfo info, Rectangle2D bounds, String problem) {}
    private record Track(String path, ResourceScript.Animation animation, AnimationTimeline timeline, double x, double y,
                         int stage, double depth, double scroll, boolean repeat, int order, boolean mirrored, int entity) {}
    private record IdlePart(String path, ResourceScript.Animation animation, int layer) {}
    private record Gear(String path, EntityScript.Part part, List<Integer> variation) {}
    private record ParticleVisual(List<IdlePart> parts, List<AnimationTimeline> timelines, int stage, double scroll, EmitterVisual child) {}
    private record EmitterVisual(String path, ParticleScript script, long initial, List<ParticleVisual> visuals) {}
    private record Emitter(EmitterVisual visual, double x, double y, double z, boolean mirrored, int entity, long seed) {}
    private record Resolved(String path, String text) {}
    @FunctionalInterface public interface TextLookup { String read(String path) throws IOException; }
    public interface SpriteLookup {
        NpkResources.Sprite sprite(String path, int index) throws IOException;
        NpkResources.Info info(String path, int index) throws IOException;
    }
}
