package studio.pvf.intellij;

import studio.pvf.preview.*;
import studio.pvf.workspace.PvfWorkspace;
import javax.swing.*;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.nio.file.*;
import java.util.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;
import static studio.pvf.intellij.VisualDungeonWorkbenchSmoke.named;

/** Real archive/resources and initialized IntelliJ workbench; no user workspace writes. */
final class GamePreviewWorkbenchSmoke {
    static void run(Path archive,Path output)throws Exception {
        var frame=edt(()->{var f=new PvfStudioFrame(output.resolve("game-workspaces"),false);f.setSize(1264,811);f.setVisible(true);return f;});
        try{
            await(()->button(frame,"打开最近的文件").isEnabled());edt(()->{frame.openArchive(archive);return null;});await(()->ready(frame));
            var workspace=(PvfWorkspace)field(frame,"workspace");var preview=(ResourcePreviewPanel)field(frame,"preview");var game=(GamePreviewPanel)field(frame,"gamePreview");
            String avatar="equipment/character/gunner/avatar/coat/auniform_wtsummer.equ",weapon="equipment/character/gunner/weapon/hcannon/n_cann_suneater.equ",title="equipment/character/common/title/joytron_handlingfeeling.equ";
            edt(()->{preview.scan(Path.of(System.getProperty("pvf.preview.npk")));return null;});
            await(()->preview.resources()!=null);
            checkNavigationHover(frame,output,title);
            edt(()->{frame.openEntry(avatar);return null;});
            await(()->ready(frame)&&loaded(game,"白色校服衬衫"));
            var tabs=edt(()->components(frame,EditorTabs.class).getFirst());var surface=edt(()->(VisualEditorSurface)tabs.getSelectedComponent());var code=edt(()->editor(tabs.getSelectedComponent()));String baseline=edt(()->code.getDocument().getText());
            edt(()->{check(surface.mode()==VisualEditorSurface.Mode.DESIGN,"Game file did not default to preview");check(named(game,"game-canvas",JPanel.class).getWidth()>300,"Preview canvas is too narrow");screenshot(frame,output.resolve("game-avatar-1264.png"));named(game,"game-timeline",JSlider.class).setValue(150);return null;});
            await(()->renderedTime(game)==150);
            edt(()->{button(game,"播放").doClick();((AnimationTimeline.Clock)field(game,"clock")).seek(current(game).duration()+300);return null;});
            await(()->renderedTime(game)>current(game).duration());edt(()->{button(game,"暂停").doClick();return null;});
            edt(()->{button(game,"朝右").doClick();surface.setMode(VisualEditorSurface.Mode.SPLIT);return null;});
            await(()->loaded(game,"白色校服衬衫"));edt(()->{check(editor(tabs.getSelectedComponent())==code,"Mode switch replaced editor");check(code.getDocument().getText().equals(baseline),"Preview changed source");screenshot(frame,output.resolve("game-split.png"));surface.setMode(VisualEditorSurface.Mode.CODE);return null;});
            edt(()->{com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(code.getProject(),()->com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(()->code.getDocument().setText(baseline.replace("白色校服衬衫","预览实时修改"))),"Game preview live edit",null);surface.setMode(VisualEditorSurface.Mode.DESIGN);return null;});
            await(()->loaded(game,"预览实时修改"));
            edt(()->{com.intellij.openapi.command.undo.UndoManager.getGlobalInstance().undo(com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(code));return null;});await(()->loaded(game,"白色校服衬衫"));
            edt(()->{frame.openEntry("equipment/character/gunner.lay");return null;});await(()->ready(frame)&&tabs.getToolTipTextAt(tabs.getSelectedIndex()).startsWith("equipment/character/gunner.lay"));
            var dependency=edt(()->editor(tabs.getSelectedComponent()));String lay=edt(()->dependency.getDocument().getText());
            edt(()->{com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(dependency.getProject(),()->com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(()->dependency.getDocument().setText(lay.replace("%s/Stay.ani","%s/Move.ani"))),"Game dependency edit",null);tabs.setSelectedComponent(surface);return null;});
            await(()->loaded(game,"白色校服衬衫")&&current(game).appearance().parts().stream().anyMatch(p->p.slot().equals("coat avatar")&&p.animation().endsWith("move.ani")));
            edt(()->{com.intellij.openapi.command.undo.UndoManager.getGlobalInstance().undo(com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(dependency));return null;});
            await(()->loaded(game,"白色校服衬衫")&&current(game).appearance().parts().stream().filter(p->p.slot().equals("coat avatar")).allMatch(p->p.animation().endsWith("stay.ani")));
            edt(()->{frame.openEntry(weapon);return null;});await(()->ready(frame)&&loaded(game,"吞日者"));
            edt(()->{var effects=named(game,"game-effect",JComboBox.class);check(effects.getItemCount()>=3,"Weapon trigger references missing");effects.setSelectedIndex(1);return null;});await(()->loaded(game,"吞日者")&&rendered(game)!=null&&rendered(game).draws().size()>10);
            edt(()->{screenshot(frame,output.resolve("game-weapon-trigger.png"));frame.openEntry(title);return null;});await(()->ready(frame)&&loaded(game,"操作快感"));
            edt(()->{screenshot(frame,output.resolve("game-title.png"));frame.setSize(1024,720);return null;});
            edt(()->{frame.validate();return null;});await(()->game.getWidth()<800);
            edt(()->{screenshot(frame,output.resolve("game-title-1024.png"));check(named(game,"game-play",JButton.class).isShowing(),"Playback disappeared in small window");check(SwingUtilities.convertRectangle(game.getParent(),game.getBounds(),frame).getMaxX()<=frame.getWidth(),"Game preview extends outside resized window");frame.setSize(1264,811);frame.openEntry("skill/gunner/laserrifle.skl");return null;});await(()->ready(frame)&&loaded(game,"激光炮"));
            edt(()->{named(game,"game-level",JSpinner.class).setValue(14);return null;});await(()->current(game)!=null&&current(game).info().rows().stream().anyMatch(r->r.label().equals("预览等级")&&r.value().startsWith("14")));
            edt(()->{named(game,"game-timeline",JSlider.class).setValue(525);return null;});
            await(()->renderedTime(game)==525&&rendered(game).draws().stream().anyMatch(d->d.label().equals("激光光束")));
            var unlearnedLaser=edt(()->current(game));
            edt(()->{
                check(unlearnedLaser.render(225,false).draws().stream().noneMatch(d->d.label().equals("蓄力光效")),
                        "Default Laser Rifle unexpectedly used its charge passive");
                selectEffect(game,"laser:normal");
                return null;
            });
            await(()->current(game)!=null&&current(game)!=unlearnedLaser);
            edt(()->{
                check(current(game).render(225,false).draws().stream().anyMatch(d->d.label().equals("蓄力光效")),
                        "The learned short-tap choice did not use the charging layer");
                return null;
            });
            edt(()->{screenshot(frame,output.resolve("game-skill.png"));frame.openEntry("skill/mage/launchjol.skl");return null;});await(()->ready(frame)&&current(game)!=null&&current(game).appearance().job().equals("mage"));
            edt(()->{check(current(game).effects().stream().anyMatch(e->e.key().equals("passive:23008")),"Mage native object binding missing");screenshot(frame,output.resolve("game-mage-jol.png"));return null;});
            checkAutomaticSkill(frame,game,output,"mage","halloweenbuster",2405,"杰克降临 · 爆炸");
            checkCombatBoxes(frame,game,output,"halloweenbuster",1);
            checkAutomaticSkill(frame,game,output,"fighter","blockbuster",220,"砖袭 · 挥击光效");
            edt(()->{
                var pose=rendered(game).draws();
                check(pose.getFirst().label().equals("砖袭 · 砖块后层"),"Brick back layer must precede the actor");
                check(pose.indexOf(skillDraw(rendered(game),"砖袭 · 挥击光效"))>pose.indexOf(skillDraw(rendered(game),"body")),
                        "Brick glow must follow the actor");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"fighter","kensin",710,"分身 · 维持");
            checkAutomaticSkill(frame,game,output,"fighter","aimvitalpoint",660,"弱点感知 · 范围");
            checkAutomaticSkill(frame,game,output,"fighter","closepunch",640,"寸拳 · 命中冲击波");
            var hitBoxes=edt(()->named(game,"game-hit-boxes",JCheckBox.class));
            edt(()->{
                check(!hitBoxes.isSelected()&&rendered(game).combatVolumes().isEmpty(),"Collision debug must start disabled");
                named(game,"game-timeline",JSlider.class).setValue(100);
                hitBoxes.doClick();
                return null;
            });
            await(()->renderedTime(game)==100&&rendered(game).combatVolumes().stream()
                    .map(CombatDebug.Volume::kind).distinct().count()==3);
            edt(()->{
                var canvas=named(game,"game-canvas",JPanel.class);
                var debugFrame=rendered(game);
                var enemy=debugFrame.combatVolumes().stream().filter(v->v.kind()==CombatDebug.Kind.ENEMY_HURT).findFirst().orElseThrow();
                var box=enemy.orientedBox();
                // At 100% zoom this is the center of the target's forward face.
                var bounds=debugFrame.bounds();
                double front=box.y()+box.depth();
                int x=(int)Math.round(canvas.getWidth()/2.0-bounds.getCenterX()+enemy.x()+box.x()+box.width()/2);
                int y=(int)Math.round(canvas.getHeight()/2.0-bounds.getCenterY()+enemy.y()+front-box.z()-box.height()/2);
                ((JComboBox<?>)field(game,"zoom")).setSelectedIndex(2);
                check(!named(game,"game-hit-box-coordinates",JCheckBox.class).isSelected()
                                &&(int)field(canvas,"selectedVolume")==-1,
                        "Collision debug must default to boxes without coordinates");
                check(canvas.getToolTipText(new MouseEvent(canvas,MouseEvent.MOUSE_MOVED,0,0,x,y,0,false))==null,
                        "Moving over an unselected box must not show its coordinates");
                screenshot(frame,output.resolve("game-combat-boxes.png"));
                button(game,"播放").doClick(); // queue another frame before selecting the frame on screen
                int selected=-1;
                for(int attempt=0;attempt<debugFrame.combatVolumes().size();attempt++){
                    clickCanvas(canvas,x,y);
                    selected=(int)field(canvas,"selectedVolume");
                    if(selected>=0&&debugFrame.combatVolumes().get(selected).equals(enemy))break;
                }
                check(selected>=0&&debugFrame.combatVolumes().get(selected).equals(enemy),"Target hitbox was not selectable");
                check(!(boolean)field(game,"playing")&&renderedTime(game)==100,"Selection did not pause on the displayed frame");
                screenshot(frame,output.resolve("game-combat-box-selected.png"));
                return null;
            });
            await(()->!(boolean)field(game,"renderPending"));
            edt(()->{
                var canvas=named(game,"game-canvas",JPanel.class);
                check(renderedTime(game)==100&&(int)field(canvas,"selectedVolume")>=0,"A pending render replaced the selected frame");
                clickCanvas(canvas,2,2);
                check((int)field(canvas,"selectedVolume")==-1,"Clicking empty space did not clear the selection");
                hitBoxes.doClick();
                return null;
            });
            await(()->rendered(game).combatVolumes().isEmpty());
            checkAutomaticSkill(frame,game,output,"swordman","ghoststepslash",720,"鬼影闪 · 目标骷髅");
            checkAutomaticSkill(frame,game,output,"swordman","reflectguard",2140,"鬼印珠 · 终结爆炸粒子");
            checkAutomaticSkill(frame,game,output,"swordman","gorecross",730,"十字斩 · 飞行血十字");
            edt(()->{
                var model=current(game);
                check(skillDraw(model.render(325,false),"十字斩刀光").x()==0,"Gore Cross lost its attached slash");
                check(model.render(589,false).draws().stream().noneMatch(d->d.label().contains("飞行血十字")),
                        "Gore Cross projectile appeared before the second slash completed");
                var projectile=skillDraw(model.render(730,false),"十字斩 · 飞行血十字");
                var mirrored=skillDraw(model.render(730,true),"十字斩 · 飞行血十字");
                check(projectile.x()==127&&projectile.y()==-82&&mirrored.x()==-127,"Gore Cross flight anchor or mirror changed");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"swordman","icewave",800,"冰刃·波动剑 · 冰柱 5");
            edt(()->{
                var model=current(game);
                var ice=skillDraw(model.render(800,false),"冰刃·波动剑 · 冰柱 5");
                check(ice.x()>70&&skillDraw(model.render(800,true),ice.label()).x()==-ice.x(),
                        "Ice Wave chained columns are missing or not mirrored");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","upperswing",340,"天击挥击");
            checkAutomaticSkill(frame,game,output,"priest","bladeofpurewhite",300,"纯白之刃挥击");
            checkAutomaticSkill(frame,game,output,"fighter","grabexplosion",1740,"擒月炎 · 爆炸");
            edt(()->{
                var model=current(game);
                check(skillDraw(model.render(550,false),"body").prepared().source().requestedFrame()==17,
                        "Grab Explosion stayed on the state-wait frame");
                check(skillDraw(model.render(650,true),"body").x()==-50,"Grab Explosion lunge did not mirror");
                check(model.render(1589,false).draws().stream().noneMatch(d->d.label().equals("擒月炎 · 爆炸")),
                        "Grab Explosion emitted before the hit frame");
                var choices=named(game,"game-effect",JComboBox.class);
                for(int i=0;i<choices.getItemCount();i++){
                    if(choices.getItemAt(i) instanceof GameEffectRecipes.Effect choice&&choice.key().equals("grabexplosion:miss")){
                        choices.setSelectedIndex(i);
                        return null;
                    }
                }
                throw new AssertionError("Grab Explosion miss preview missing");
            });
            await(()->current(game)!=null&&current(game).duration()==750&&renderedTime(game)>0);
            edt(()->{
                check(current(game).render(650,false).draws().stream().noneMatch(d->d.label().startsWith("擒月炎")),
                        "A missed grab displayed the on-grab effect");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"swordman","bloodblast",1220,"怒气爆发 · 血柱");
            checkCombatBoxes(frame,game,output,"bloodblast",4);
            edt(()->{
                var model=current(game);
                var early=model.render(1199,true);
                var floor=skillDraw(early,"怒气爆发 · 地面血阵");
                check(floor.y()==50&&!floor.mirrored(),"Blood Blast ground pass position or facing is wrong");
                check(early.draws().stream().noneMatch(d->d.label().equals("怒气爆发 · 血柱")),
                        "Blood Blast column ignored the authored lead-in");
                check(skillDraw(model.render(1200,true),"怒气爆发 · 血柱").mirrored(),
                        "Blood Blast column did not follow caster facing");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"fighter","energyball",500,"念气波 · 飞行");
            edt(()->{
                var model=current(game);
                check(model.render(319,false).draws().stream().noneMatch(d->d.label().equals("念气波 · 飞行")),
                        "Nen Shot launched before the release animation's second frame");
                var shot=skillDraw(model.render(320,false),"念气波 · 飞行");
                check(shot.x()==70&&shot.y()==-63&&skillDraw(model.render(500,true),"念气波 · 飞行").x()==-142,
                        "Nen Shot origin or horizontal travel is wrong");
                named(game,"game-level",JSpinner.class).setValue(30);
                return null;
            });
            await(()->current(game)!=null&&current(game).duration()==1470);
            edt(()->{
                check(skillDraw(current(game).render(320,false),"念气波 · 飞行").prepared().transform().getScaleX()==2,
                        "Nen Shot Lv30 did not use its configured size");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"fighter","kikouken",1020,"螺旋念气场 · 念气场");
            edt(()->{
                var model=current(game);
                var charged=model.render(699,false);
                skillDraw(charged,"螺旋念气场 · 聚气");
                check(charged.draws().stream().noneMatch(d->d.label().equals("螺旋念气场 · 念气场")),
                        "Spiral Nen Field started before the push");
                var push=model.render(700,false);
                check(skillDraw(push,"body").prepared().source().requestedFrame()==128,
                        "Spiral Nen Field did not switch to the original push animation");
                var field=skillDraw(push,"螺旋念气场 · 念气场");
                var mirrored=skillDraw(model.render(1739,true),"螺旋念气场 · 念气场");
                check(field.x()==107&&field.y()==-61&&mirrored.x()==-107&&mirrored.y()==-61,
                        "Spiral Nen Field spawn anchor or facing is wrong");
                check(model.render(1740,false).draws().stream().noneMatch(d->d.label().equals("螺旋念气场 · 念气场")),
                        "Spiral Nen Field continued past its last authored frame");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"fighter","legsuplex",630,"空绞锤 · 地面冲击");
            edt(()->{
                var model=current(game);
                check(skillDraw(model.render(450,false),"body").y()<0,"Airsteiner lost the airborne drop");
                check(skillDraw(model.render(560,true),"空绞锤 · 地面冲击").x()==28,
                        "Airsteiner ground impact did not mirror");
                selectEffect(game,"legsuplex:miss");
                return null;
            });
            await(()->current(game)!=null&&current(game).duration()<900&&renderedTime(game)>0);
            edt(()->{
                check(current(game).render(250,false).draws().stream().noneMatch(d->d.label().contains("落地光效")||d.label().contains("地面冲击")),
                        "Missed Airsteiner displayed the successful throw's impact");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","launchfrosthead",1020,"冰霜雪人 · 落地");
            edt(()->{
                check(current(game).appearance().action().equals("throw motion 4-1"),"Frost Head lost the fourth throw group");
                selectEffect(game,"mage-element:charged");
                return null;
            });
            await(()->current(game)!=null&&current(game).duration()>3500&&renderedTime(game)>0);
            edt(()->{
                check(current(game).render(1299,false).draws().stream().noneMatch(d->d.label().equals("冰霜雪人 · 跳跃")),
                        "Charged Frost Head appeared before release");
                skillDraw(current(game).render(1400,false),"冰霜雪人 · 跳跃");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","magicmissile",650,"魔法星弹 · 飞行");
            edt(()->{
                var shot=rendered(game);
                var trail=shot.draws().stream().filter(d->d.label().equals("魔法星弹 · 拖尾")).toList();
                check(trail.size()>1&&trail.stream().map(d->d.x()).distinct().count()>1,
                        "Magic Missile trail did not follow the projectile's emitted path");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","aurashield",1200,"魔法盾 · 维持");
            edt(()->{
                var model=current(game);
                check(skillDraw(model.render(1040,false),"魔法盾 · 展开").y()==0,"Aura Shield left its character anchor");
                var icon=skillDraw(model.render(1200,true),"魔法盾 · 生效图标");
                check(icon.prepared().source().requestedFrame()==32&&!icon.mirrored(),"Aura Shield buff icon substitution failed");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","chasersexplosion",1690,"炫纹强压 · 爆炸");
            edt(()->{
                var model=current(game);
                check(skillDraw(model.render(900,false),"body").y()==-70,"Chaser Press lost its airborne hold");
                check(model.render(1639,false).draws().stream().noneMatch(d->d.label().equals("炫纹强压 · 爆炸")),
                        "Chaser Press exploded before the impact frame");
                check(skillDraw(model.render(1690,true),"炫纹强压 · 爆炸").x()==-77,"Chaser Press impact did not mirror");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","dragonspear",3650,"煌龙偃月 · 最终爆炸");
            edt(()->{
                var model=current(game);
                var gathering=model.render(2100,false);
                check(gathering.draws().stream().anyMatch(d->d.label().equals("coat avatar")),
                        "Dragon Spear lost its costume on the held pose");
                check(skillDraw(gathering,"煌龙偃月 · 龙之炫纹").x()==322,"Dragon Spear chasers are not attached to the tip");
                check(skillDraw(model.render(3650,true),"煌龙偃月 · 最终爆炸").x()==-322,
                        "Dragon Spear blast did not mirror");
                var choices=named(game,"game-effect",JComboBox.class);
                for(int i=0;i<choices.getItemCount();i++){
                    if(choices.getItemAt(i) instanceof GameEffectRecipes.Effect choice&&choice.key().equals("dragonspear:miss")){
                        choices.setSelectedIndex(i);
                        return null;
                    }
                }
                throw new AssertionError("Dragon Spear miss preview missing");
            });
            await(()->current(game)!=null&&current(game).duration()<3000&&renderedTime(game)>0);
            edt(()->{
                var missed=current(game).render(1950,false);
                skillDraw(missed,"煌龙偃月 · 消散");
                check(missed.draws().stream().noneMatch(d->d.label().contains("爆炸")||d.label().contains("炫纹")),
                        "A missed Dragon Spear displayed the on-hit effects");
                named(game,"game-timeline",JSlider.class).setValue(1950);
                return null;
            });
            await(()->renderedTime(game)==1950);
            edt(()->{screenshot(frame,output.resolve("game-dragonspear-miss.png"));return null;});
            checkAutomaticSkill(frame,game,output,"mage","blackhole",4850,"湮灭黑洞 · 爆裂粒子");
            edt(()->{
                var model=current(game);
                var vortex=skillDraw(model.render(3120,true),"湮灭黑洞 · 涡流");
                var floor=skillDraw(model.render(3120,true),"湮灭黑洞 · 地面投影");
                check(vortex.x()==-200&&vortex.y()==-80&&floor.y()==0,"Black Hole height or ground projection is wrong");
                check(model.render(4499,false).draws().stream().noneMatch(d->d.label().equals("湮灭黑洞 · 爆裂")),
                        "Black Hole collapsed before its level duration");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","backstep",570,"后跳 · 落地扬尘");
            edt(()->{
                var model=current(game);
                var air=skillDraw(model.render(200,false),"body");
                var land=skillDraw(model.render(570,false),"body");
                check(air.x()<-55&&air.y()<-45&&land.x()<-140&&land.y()==0,"Backstep airborne and ground positions are wrong");
                check(skillDraw(model.render(200,true),"body").x()==-air.x(),"Backstep did not reverse its movement");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"gunner","jumppowerup",850,"跃翔 · 生效图标");
            edt(()->{
                var charge=current(game).render(200,false);
                check(skillDraw(charge,"跃翔 · 聚气").y()==-157,"Shared buff charge is not anchored above the head");
                check(charge.draws().stream().anyMatch(d->d.label().equals("跃翔 · 聚气粒子")&&d.y()<-135),
                        "Shared buff charge particles remained near the body");
                named(game,"game-timeline",JSlider.class).setValue(200);
                return null;
            });
            await(()->renderedTime(game)==200);
            edt(()->{screenshot(frame,output.resolve("game-jumppowerup-charge.png"));return null;});
            checkAutomaticSkill(frame,game,output,"priest","slowheal",1150,"缓慢愈合 · 持续前层");
            edt(()->{
                check(current(game).appearance().action().equals("throw motion 3-1"),"Slow Heal did not use Deliver");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"priest","cure",620,"净化 · 施放");
            checkAutomaticSkill(frame,game,output,"priest","hpmaxupparty",1120,"天堂谐音 · 地面光效");
            checkAutomaticSkill(frame,game,output,"priest","flashglobe",1020,"圣光球 · 飞行");
            checkAutomaticSkill(frame,game,output,"priest","antiairupper",779,"coat avatar");
            edt(()->{
                var draws=rendered(game).draws();
                int body=draws.indexOf(skillDraw(rendered(game),"body"));
                int coat=draws.indexOf(skillDraw(rendered(game),"coat avatar"));
                check(body>=0&&coat>body,"Priest coat drew beneath the body");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"priest","fastheal",600,"快速愈合 · 目标效果");
            edt(()->{
                check(skillDraw(rendered(game),"快速愈合 · 目标效果").y()==-66,"Fast Heal did not use recipient height");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"priest","repeatedsmash",2600,"狂乱锤击 · 终结冲击波");
            checkAutomaticSkill(frame,game,output,"priest","homerun",1370,"星落打 · 命中冲击");
            var homeRunHit=edt(()->current(game));
            edt(()->{selectEffect(game,"homerun:miss");return null;});
            await(()->current(game)!=null&&current(game)!=homeRunHit);
            edt(()->{
                var miss=current(game);
                check(miss.duration()==730,"Home Run miss did not stop after the grab attempt");
                check(miss.render(420,false).draws().stream().noneMatch(d->d.label().startsWith("星落打 · ")),
                        "Home Run miss replayed hit effects");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"priest","sacrificeofsoul",650,"灵魂牺牲 · 队友恢复");
            checkAutomaticSkill(frame,game,output,"priest","shadowboxer",1000,"附加角色 · body");
            edt(()->{
                var pose=rendered(game);
                var echo=skillDraw(pose,"附加角色 · body");
                check(echo.opacity()<1&&echo.x()!=skillDraw(pose,"body").x(),"Shadow Boxer replay did not trail the moving punch");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"priest","quakearea",450,"落凤锤 · 空中挥击");
            checkAutomaticSkill(frame,game,output,"priest","smasher",870,"虎袭 · 冲刺光效");
            var smasherHit=edt(()->current(game));
            edt(()->{
                check(rendered(game).draws().stream().anyMatch(d->d.label().equals("虎袭 · 被抓住的目标")),
                        "Smasher did not draw its captured target");
                selectEffect(game,"smasher:miss");
                return null;
            });
            await(()->current(game)!=null&&current(game)!=smasherHit);
            edt(()->{
                check(current(game).render(100,false).draws().stream().noneMatch(d->d.label().startsWith("虎袭 · ")),
                        "Smasher miss rendered the hit branch");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"fighter","junkspin",1300,"街头风暴 · 投掷物");
            checkAutomaticSkill(frame,game,output,"fighter","nenguard",1800,"念气罩 · 循环罩体");
            checkAutomaticSkill(frame,game,output,"fighter","nenflower",6650,"千莲怒放 · 爆炸");
            checkAutomaticSkill(frame,game,output,"swordman","bremen",637,"侵蚀之普戾蒙 · 鬼神");
            edt(()->{
                check(current(game).appearance().action().equals("throw motion 1-1"),"Bremen used the wrong cast group");
                check(skillDraw(rendered(game),"body").prepared().source().requestedFrame()>=165,
                        "Bremen recovery did not extend the swordman's arm");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"swordman","frenzy",637,"血之狂暴 · 爆发");
            edt(()->{
                check(skillDraw(rendered(game),"body").prepared().source().requestedFrame()==167,
                        "Frenzy effect did not use the synchronized forward-arm pose");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"swordman","weaponcombo",500,"里鬼剑术 · 刀光");
            var shortSword=edt(()->current(game));
            edt(()->{selectEffect(game,"weapon:5");return null;});
            await(()->current(game)!=null&&current(game)!=shortSword);
            edt(()->{
                var lightsaber=current(game).appearance();
                check(lightsaber.wearing().getOrDefault("weapon","").contains("/beamsword/"),"Weapon branch did not equip a lightsaber");
                check(lightsaber.action().equals("etc motion:40"),"Lightsaber branch did not switch body animation");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"swordman","wavespinarea",6800,"不动明王阵 · 焰珠爆炸");
            var singleOrb=edt(()->current(game));
            edt(()->{selectEffect(game,"act1-wave-marks-5");return null;});
            await(()->current(game)!=null&&current(game)!=singleOrb);
            edt(()->{
                var draws=current(game).render(2400,false).draws();
                long orbs=draws.stream().filter(d->d.label().equals("不动明王阵 · 焰珠旋转"))
                        .map(d->List.of(d.x(),d.y())).distinct().count();
                check(orbs==5&&draws.stream().anyMatch(d->d.label().equals("不动明王阵 · 目标拘束光效")),
                        "Wave Spin did not draw five orbs and a held target");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"mage","magicaltempoup",1950,"魔法秀 · 持续粒子");
            checkAutomaticSkill(frame,game,output,"mage","pokethrow",1010,"圆舞棍 · 砸地目标");
            checkAutomaticSkill(frame,game,output,"gunner","laserriflecharge",1375,"激光起始");
            edt(()->{
                var laser=current(game);
                check(laser.render(200,false).draws().stream().noneMatch(d->d.label().equals("蓄力光效")),
                        "Laser charge appeared before its trigger frame");
                check(laser.render(1050,false).draws().stream().anyMatch(d->d.label().equals("满蓄力光效")),
                        "Charged laser lost the full-charge confirmation flash");
                var fired=laser.render(1375,false).draws();
                check(fired.stream().filter(d->d.label().equals("激光炮枪体")).count()==2
                                &&fired.stream().anyMatch(d->d.label().equals("激光光束"))
                                &&fired.stream().noneMatch(d->d.label().equals("收枪枪光")||d.label().equals("收枪火花")),
                        "Charged laser lost its original gun layers at release");
                var recovery=laser.render(1955,false).draws();
                check(recovery.stream().anyMatch(d->d.label().equals("收枪火花"))
                                &&recovery.stream().noneMatch(d->d.label().equals("激光光束")),
                        "Charged laser recovery overlapped the beam or lost its spark");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"gunner","turningshoot",350,"精准射击 · 子弹");
            var turningModel=edt(()->current(game));
            edt(()->{
                var model=current(game);
                var turned=model.render(350,false);
                check(skillDraw(turned,"body").mirrored()&&skillDraw(turned,"精准射击 · 子弹").x()<0,
                        "Turning Shoot did not reverse the actor and the projectile");
                check(skillDraw(turned,"枪口火光").mirrored(),"Turning Shoot's weapon flash was missing or facing forward");
                selectEffect(game,"precision:down");
                return null;
            });
            await(()->current(game)!=null&&current(game)!=turningModel&&renderedTime(game)>0);
            checkAutomaticSkill(frame,game,output,"gaebolgpunch",3250,"盖波加拳击");
            edt(()->{
                var model=current(game);
                var early=skillDraw(model.render(1950,false),"盖波加拳击");
                var extended=skillDraw(model.render(3250,false),"盖波加拳击");
                var retracting=skillDraw(model.render(4850,false),"盖波加拳击");
                var left=skillDraw(model.render(3250,true),"盖波加拳击");
                check(extended.x()-early.x()>350&&extended.x()-retracting.x()>100,"Gaebolg fist must travel and retract");
                check(extended.clipX()<extended.x()&&left.clipX()==-extended.clipX(),"Gaebolg portal mask did not mirror");
                check(model.render(2849,false,false,true).combatVolumes().stream().noneMatch(v->v.kind()==CombatDebug.Kind.ATTACK)
                                && model.render(2850,false,false,true).combatVolumes().stream().filter(v->v.kind()==CombatDebug.Kind.ATTACK).count()==2
                                && model.render(3349,false,false,true).combatVolumes().stream().filter(v->v.kind()==CombatDebug.Kind.ATTACK).count()==2
                                && model.render(3350,false,false,true).combatVolumes().stream().noneMatch(v->v.kind()==CombatDebug.Kind.ATTACK),
                        "Gaebolg's native 500 ms collision window drifted");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"swordman","saya",1600,"冰霜之萨亚 · 法阵维持");
            edt(()->{
                var volumes=current(game).render(1500,false,false,true).combatVolumes();
                var range=volumes.stream().filter(v->v.targetRange()!=null).findFirst().orElseThrow();
                check(range.x()==122&&range.targetRange().radius()==250&&range.targetRange().maxZ()==70
                        &&range.pulse(),"Saya lost the configured position, shape or cadence");
                return null;
            });
            checkCombatBoxes(frame,game,output,"saya",1);
            checkAutomaticSkill(frame,game,output,"gatling",1400,"格林机枪弹道");
            checkAutomaticSkill(frame,game,output,"flamethrower",2876,"火焰喷射器火焰");
            checkFlameJet(game,"火焰喷射器火焰");
            checkCombatBoxes(frame,game,output,"flamethrower",6);
            checkAutomaticSkill(frame,game,output,"flamethrowerboost",2876,"烈焰强击火焰");
            checkFlameJet(game,"烈焰强击火焰");
            checkCombatBoxes(frame,game,output,"flamethrowerboost",4);
            checkAutomaticSkill(frame,game,output,"satellitelaser",4813,"卫星射线 · 光柱 0");
            checkCombatBoxes(frame,game,output,"satellitelaser",3);
            checkAutomaticSkill(frame,game,output,"bulletguard",0,"body");
            edt(()->{
                var pose=rendered(game).draws();
                var body=skillDraw(rendered(game),"body");
                var clothes=pose.stream().filter(d->d.label().endsWith(" avatar")).toList();
                check(body.prepared().source().requestedFrame()==185&&!clothes.isEmpty(),"Bullet Guard used the wrong body pose");
                check(clothes.stream().allMatch(d->d.prepared().source().requestedFrame()==185),"Bullet Guard clothing and body frames differ");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"fastknee",220,"body");
            edt(()->{
                var pose=rendered(game).draws();
                int body=pose.stream().filter(d->d.label().equals("body")).findFirst().orElseThrow().prepared().source().requestedFrame();
                var clothes=pose.stream().filter(d->d.label().endsWith(" avatar")).toList();
                check(body==162&&!clothes.isEmpty(),"Fast Knee did not reach the kick frame");
                check(clothes.stream().allMatch(d->d.prepared().source().requestedFrame()==body),"Fast Knee clothing and body frames differ");
                return null;
            });
            checkAutomaticSkill(frame,game,output,"crossshoot",220,"交叉射击弹道");
            edt(()->{
                check(rendered(game).draws().stream().anyMatch(d->d.label().equals("交叉射击枪口")),"Cross Shot muzzle effect missing");
                return null;
            });
            edt(()->{frame.openEntry("skill/gunner/armormasteryleather.skl");return null;});
            await(()->ready(frame)&&current(game)!=null&&!current(game).unavailableReason().isEmpty());
            edt(()->{
                check(rendered(game)==null,"Unbound skill displayed a waiting pose");
                check(!named(game,"game-play",JButton.class).isEnabled(),"Unbound skill offers empty playback");
                var choices=named(game,"game-effect",JComboBox.class);
                for(int i=0;i<choices.getItemCount();i++){
                    if(choices.getItemAt(i) instanceof GameEffectRecipes.Effect choice&&choice.key().equals("actor")){
                        choices.setSelectedIndex(i);
                        return null;
                    }
                }
                throw new AssertionError("Unbound skill missing manual action choice");
            });
            await(()->current(game)!=null&&rendered(game)!=null&&!rendered(game).draws().isEmpty());
            String stackable=workspace.paths().stream().filter(p->p.startsWith("stackable/")&&p.endsWith(".stk")).sorted().findFirst().orElseThrow();
            edt(()->{frame.openEntry(stackable);return null;});await(()->ready(frame)&&current(game)!=null&&current(game).info().type().equals("物品"));
            edt(()->{check(((VisualEditorSurface)tabs.getSelectedComponent()).mode()==VisualEditorSurface.Mode.DESIGN,"STK did not default to preview");frame.openEntry(title);frame.openEntry(weapon);frame.openEntry(avatar);return null;});await(()->loaded(game,"白色校服衬衫"));
            var oldResources=edt(preview::resources);edt(()->{preview.scan(Path.of(System.getProperty("pvf.preview.npk")));return null;});await(()->preview.resources()!=oldResources&&loaded(game,"白色校服衬衫"));
            edt(()->{check(workspace.dirtyPaths().isEmpty(),"Pure preview created script drafts");return null;});
            workspace.archive().verifyUnchanged();Files.writeString(output.resolve("game-preview-result.txt"),"PASS: Airsteiner grab/miss and ground impact, chained Ice Wave, Frost Head and charged variant, Turning Shoot facing and Down choice, Priest Deliver/Blast and Holy Orb, Spiral Nen Field charge/push, Dragon Spear hit/miss and held costume, Gore Cross flight, shared buff charge anchor, Aura Shield and BuffIcon substitution, Chaser Press height/impact, Black Hole phase clocks, Backstep arc/dust, shared and priest buffs, Grab Explosion hit/miss and lunge, Blood Blast layer and 1200ms gate, Nen Shot direction and level, Magic Missile, icon and description on tree and search hover without opening a tab, timed skill layers, Bullet Guard and Fast Knee poses, Gaebolg fist travel and mirrored mask, Cross Shot muzzle and projectile, both flame jets at 2876 ms, automatic skills, ACT1 tooltip, real EQU/SKL/STK, preview/code/split, seek/loop/facing, live top-level and LAY edit/undo, resource reindex, 1264/1024 layouts, no preview draft changes\n");
        }catch(Exception|AssertionError failure){edt(()->{screenshot(frame,output.resolve("game-preview-failure.png"));return null;});throw failure;}
        finally{edt(()->{respond("放弃未保存编辑");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(()->!frame.isDisplayable());}
    }
    private static void checkNavigationHover(PvfStudioFrame frame,Path output,String title)throws Exception {
        var navigationHover=edt(()->field(frame,"navigationHover"));
        var search=edt(()->(JTextField)field(frame,"pathSearch"));
        var results=edt(()->(JList<?>)field(frame,"resultList"));
        var tree=edt(()->(JTree)field(frame,"tree"));
        var tabs=edt(()->components(frame,EditorTabs.class).getFirst());
        int tabCount=edt(tabs::getTabCount);
        edt(()->{search.setText(title);return null;});
        await(()->results.isShowing()&&results.getModel().getSize()==1&&title.equals(results.getModel().getElementAt(0)));
        edt(()->{moveOver(results,results.getCellBounds(0,0));return null;});
        await(()->hoverLoaded(navigationHover,"操作快感"));
        edt(()->{
            screenshot((GameTooltipPanel)field(navigationHover,"card"),output.resolve("game-hover-item.png"));
            check(tabs.getTabCount()==tabCount,"Hover opened a tab");
            search.setText("");
            return null;
        });
        await(()->tree.isShowing()&&!hoverLoaded(navigationHover,"操作快感"));
        var skillPath=edt(()->{
            var path=new TreePath(tree.getModel().getRoot());
            for(String segment:List.of("skill","fighter","blockbuster.skl")){
                tree.expandPath(path);
                var node=(DefaultMutableTreeNode)path.getLastPathComponent();
                var child=Collections.list(node.children()).stream()
                        .filter(n->n.toString().equals(segment)).findFirst().orElseThrow();
                path=path.pathByAddingChild(child);
            }
            tree.scrollPathToVisible(path);
            return path;
        });
        await(()->tree.getVisibleRect().intersects(tree.getPathBounds(skillPath)));
        edt(()->{moveOver(tree,tree.getPathBounds(skillPath));return null;});
        await(()->hoverLoaded(navigationHover,"砖袭"));
        edt(()->{
            screenshot((GameTooltipPanel)field(navigationHover,"card"),output.resolve("game-hover-skill.png"));
            check(tabs.getTabCount()==tabCount,"Tree hover opened a tab");
            tree.dispatchEvent(new MouseEvent(tree,MouseEvent.MOUSE_EXITED,System.currentTimeMillis(),0,-1,-1,0,false));
            check(!hoverLoaded(navigationHover,"砖袭"),"Card stayed visible after leaving the tree");
            return null;
        });
    }

    private static boolean hoverLoaded(Object hover,String name){
        var card=(GameTooltipPanel)field(hover,"card");
        return card!=null&&card.isShowing()&&card.item()!=null&&card.item().name().contains(name)&&field(card,"icon")!=null;
    }

    private static void moveOver(JComponent owner,Rectangle bounds){
        owner.dispatchEvent(new MouseEvent(owner,MouseEvent.MOUSE_MOVED,System.currentTimeMillis(),0,
                bounds.x+8,bounds.y+bounds.height/2,0,false));
    }

    private static GamePreview.Draw skillDraw(GamePreview.Rendered frame,String label){
        return frame.draws().stream().filter(d->d.label().equals(label)).findFirst().orElseThrow();
    }
    private static void selectEffect(GamePreviewPanel game,String key){
        var choices=named(game,"game-effect",JComboBox.class);
        for(int index=0;index<choices.getItemCount();index++){
            if(choices.getItemAt(index) instanceof GameEffectRecipes.Effect effect&&effect.key().equals(key)){
                choices.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("Missing preview choice "+key);
    }
    private static void checkFlameJet(GamePreviewPanel game,String label)throws Exception{
        edt(()->{
            var jet=rendered(game).draws().stream().filter(draw->draw.label().equals(label)
                    &&draw.prepared().source().requestedFrame()==0).toList();
            check(!jet.isEmpty(),label+" has no visible jet at 2876 ms");
            for(var draw:jet){
                var transform=draw.prepared().transform();
                double angle=Math.atan2(transform.getShearY(),transform.getScaleX());
                check(Math.abs(angle)<.01,label+" rotates away from its muzzle");
            }
            return null;
        });
    }
    private static void checkAutomaticSkill(PvfStudioFrame frame,GamePreviewPanel game,Path output,String file,int at,String track)throws Exception {
        checkAutomaticSkill(frame,game,output,"gunner",file,at,track);
    }
    private static void checkAutomaticSkill(PvfStudioFrame frame,GamePreviewPanel game,Path output,String job,String file,int at,String track)throws Exception {
        GamePreview previous=edt(()->current(game));
        edt(()->{frame.openEntry("skill/"+job+"/"+file+".skl");return null;});
        await(()->ready(frame)&&current(game)!=null&&current(game)!=previous&&current(game).usingSequence()&&renderedTime(game)>0);
        edt(()->{
            var info=named(game,"game-item-info",GameTooltipPanel.class);
            check(info.getFont().getFamily(Locale.ENGLISH).equals("SimSun")&&info.getFont().getSize()==16,"ACT1 tooltip font or screenshot scale differs");
            check(info.item().tooltip().heading().contains(" Lv"),"Skill level is missing from tooltip heading");
            named(game,"game-timeline",JSlider.class).setValue(at);
            return null;
        });
        await(()->renderedTime(game)==at&&rendered(game).draws().stream().anyMatch(draw->draw.label().equals(track)));
        edt(()->{screenshot(frame,output.resolve("game-"+file+".png"));return null;});
        if(file.equals("gaebolgpunch")){
            var surface=edt(()->(VisualEditorSurface)components(frame,EditorTabs.class).getFirst().getSelectedComponent());
            var before=edt(()->current(game));
            edt(()->{surface.setMode(VisualEditorSurface.Mode.CODE);return null;});
            await(()->String.valueOf(field(frame,"previewSignature")).startsWith("game|skill/gunner/"+file+".skl|false|"));
            edt(()->{surface.setMode(VisualEditorSurface.Mode.DESIGN);return null;});
            await(()->current(game)!=null&&current(game)!=before&&renderedTime(game)>0);
        }
    }
    private static void checkCombatBoxes(PvfStudioFrame frame,GamePreviewPanel game,Path output,String skill,int expected)throws Exception{
        var boxes=edt(()->named(game,"game-hit-boxes",JCheckBox.class));
        edt(()->{boxes.doClick();return null;});
        await(()->rendered(game).combatVolumes().stream().filter(v->v.kind()==CombatDebug.Kind.ATTACK).count()==expected);
        edt(()->{
            screenshot(frame,output.resolve("game-"+skill+"-combat.png"));
            boxes.doClick();
            return null;
        });
        await(()->rendered(game).combatVolumes().isEmpty());
    }
    private static void clickCanvas(JPanel canvas,int x,int y){
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,MouseEvent.BUTTON1_DOWN_MASK,x,y,1,false,MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,0,0,x,y,1,false,MouseEvent.BUTTON1));
    }
    private static Object field(Object owner,String name){try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private static GamePreview current(GamePreviewPanel panel){return (GamePreview)field(panel,"model");}
    private static boolean loaded(GamePreviewPanel panel,String name){var m=current(panel);return m!=null&&m.info().name().contains(name);}
    private static GamePreview.Rendered rendered(GamePreviewPanel panel){var canvas=components(panel,JPanel.class).stream().filter(p->"game-canvas".equals(p.getName())).findFirst().orElseThrow();return (GamePreview.Rendered)field(canvas,"frame");}
    private static long renderedTime(GamePreviewPanel panel){var frame=rendered(panel);return frame==null?-1:frame.milliseconds();}
    private static boolean ready(PvfStudioFrame frame){return frame.isEnabled()&&button(frame,"打开 PVF…").isEnabled();}
}
