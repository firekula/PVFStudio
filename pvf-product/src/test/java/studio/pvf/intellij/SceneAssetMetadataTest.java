package studio.pvf.intellij;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SceneAssetMetadataTest {
 @Test void classificationUsesNativeTypeAndNameRatherThanFilename() throws Exception {
  assertTrue(SceneAssetMetadata.describe(SceneResourceBrowser.Category.TILE,"[tile type] `extended` [pass type] ").contains("扩展地块"));
  assertTrue(SceneAssetMetadata.describe(SceneResourceBrowser.Category.MONSTER,"[name] `怪物名字` [level] 1").contains("怪物名字"));
  assertThrows(java.io.IOException.class,() -> SceneAssetMetadata.describe(SceneResourceBrowser.Category.TILE,"[tile type] `invented`"));
 }
}
