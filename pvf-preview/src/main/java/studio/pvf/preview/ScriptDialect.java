package studio.pvf.preview;

import java.util.Locale;

/** Canonical preview keywords only; source text, quoted paths and strings stay untouched. */
final class ScriptDialect {
    private ScriptDialect() {}
    static String tag(String raw) {
        String tag = raw.trim().toLowerCase(Locale.ROOT);
        if (tag.startsWith("/")) return "/" + tag(tag.substring(1));
        return switch (tag) {
            case "타입" -> "type";
            case "타일" -> "tile";
            case "확장 타일" -> "extended tile";
            case "타일 타입" -> "tile type";
            case "통과 속성" -> "pass type";
            case "원경 스크롤" -> "far sight scroll";
            case "중경 스크롤" -> "middle sight scroll";
            case "접경 스크롤" -> "near sight scroll";
            case "애니메이션" -> "animation";
            case "배경 애니메이션" -> "background animation";
            case "통로 오브젝트 좌표" -> "pathgate pos";
            case "몬스터" -> "monster";
            case "몬스터 조건" -> "monster condition";
            case "통로 오브젝트" -> "pathgate object";
            case "미로 정보" -> "maze info";
            case "크기" -> "size";
            case "그리드" -> "greed";
            case "시작 맵" -> "start map";
            case "보스 맵" -> "boss map";
            case "맵 지정" -> "map specification";
            case "보스 맵 지정" -> "boss map specification";
            case "던전" -> "dungeon";
            case "필드 애니메이션" -> "field animation";
            case "대기 모션" -> "waiting motion";
            case "기본 모션" -> "basic motion";
            case "레이어" -> "layer";
            case "폭" -> "width";
            case "정수 자료" -> "int data";
            case "문자열 자료" -> "string data";
            default -> tag;
        };
    }
    static String flag(String raw) {
        String flag = raw.replace("[", "").replace("]", "").toLowerCase(Locale.ROOT).trim();
        return switch (flag) {
            case "원경" -> "distantback";
            case "중경" -> "middleback";
            case "접경" -> "closeback";
            case "바닥" -> "bottom";
            case "일반" -> "normal";
            case "근경" -> "close";
            case "보스" -> "boss";
            case "아래" -> "below";
            case "위" -> "upper";
            case "왼쪽" -> "left";
            case "오른쪽" -> "right";
            case "모두 통과", "통과 가능" -> "pass all";
            case "부유형만" -> "pass only float type";
            case "통과 불가" -> "do not pass";
            default -> flag;
        };
    }
}
