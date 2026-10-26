package studio.pvf.intellij;
import studio.pvf.preview.DungeonPreview;
/** One role decision feeds rendering, navigation choices and clone requests. */
final class DungeonEditorPreview {
 static DungeonPanel.Selection resolve(DungeonPreview dungeon,DungeonEditorPanel.Preview selected){
  var maze=dungeon.definition().mazes().get(selected.maze());var layout=dungeon.arrange(selected.maze(),maze.starts().contains(selected.position())?selected.position():maze.starts().isEmpty()?null:maze.starts().getFirst(),maze.bosses().contains(selected.position())?selected.position():maze.bosses().isEmpty()?null:maze.bosses().getFirst(),0,selected.hell());var room=layout.room(selected.position());if(room==null)return null;var map=room.map();if(!room.hell()&&selected.mapId()!=null)map=room.choices().stream().filter(m -> m.id()==selected.mapId()).findFirst().orElse(map);return new DungeonPanel.Selection(dungeon,layout,room,map,true,true,0);
 }
}
