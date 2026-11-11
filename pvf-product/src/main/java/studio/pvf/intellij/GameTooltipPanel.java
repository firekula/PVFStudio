package studio.pvf.intellij;

import studio.pvf.preview.GameItemInfo;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** SimSun from the ACT1 initializer (0x40B630), at the reference screenshot's display scale. */
final class GameTooltipPanel extends JPanel implements Scrollable {
    private static final int CARD_WIDTH=234;
    private static final int MARGIN=7;
    private static final int PADDING=7;
    static final int DISPLAY_FONT_SIZE=16;
    private static final int LINE_HEIGHT=17;
    private static final Color NORMAL=new Color(250,250,250);
    private static final Color BONUS=new Color(106,221,249);
    private static final Color FLAVOR=new Color(177,177,177);

    private GameItemInfo item;
    private String status="";
    private Card layout;

    private record Text(String value,int x,int baseline,Color color) {}
    private record Card(int width,int height,List<Text> text) {}

    GameTooltipPanel() {
        setName("game-item-info");
        setFont(new Font("SimSun",Font.PLAIN,DISPLAY_FONT_SIZE));
    }

    void showItem(GameItemInfo value) {
        item=value;
        status="";
        getAccessibleContext().setAccessibleName(value.tooltip().heading());
        invalidateCard();
    }

    void showStatus(String value) {
        item=null;
        status=value;
        getAccessibleContext().setAccessibleName(value);
        invalidateCard();
    }

    GameItemInfo item(){return item;}

    private void invalidateCard(){
        layout=null;
        revalidate();
        repaint();
    }

    private int cardWidth(){
        int available=getParent()==null?getWidth():getParent().getWidth();
        return available<=0?CARD_WIDTH:Math.min(CARD_WIDTH,Math.max(80,available-2*MARGIN));
    }

    private Card card(){
        int width=cardWidth();
        if(layout!=null&&layout.width()==width)return layout;
        FontMetrics font=getFontMetrics(getFont());
        var text=new ArrayList<Text>();
        int y=PADDING+font.getAscent();
        int lineWidth=width-2*PADDING;
        if(item==null){
            y=block(text,font,status,PADDING,y,lineWidth,NORMAL);
        }else{
            Color name=titleColor(item);
            y=block(text,font,item.tooltip().heading(),PADDING,y,lineWidth,name);
            y=block(text,font,item.english(),PADDING,y,lineWidth,name);
            y+=7;
            for(var row:item.tooltip().lines()){
                if(row.left().isEmpty()&&row.right().isEmpty()){
                    y+=8;
                    continue;
                }
                Color color=switch(row.ink()){
                    case NORMAL -> NORMAL;
                    case BONUS -> BONUS;
                    case FLAVOR -> FLAVOR;
                };
                int rightWidth=font.stringWidth(row.right());
                if(!row.right().isEmpty()&&rightWidth<=lineWidth&&font.stringWidth(row.left())+rightWidth+5<=lineWidth){
                    if(!row.left().isEmpty())text.add(new Text(row.left(),PADDING,y,color));
                    Color right=row.right().startsWith("MP ")?BONUS:color;
                    text.add(new Text(row.right(),width-PADDING-rightWidth,y,right));
                    y+=LINE_HEIGHT;
                }else{
                    y=block(text,font,row.left(),PADDING,y,lineWidth,color);
                    y=block(text,font,row.right(),PADDING,y,lineWidth,color);
                }
            }
        }
        layout=new Card(width,Math.max(28,y-font.getAscent()+PADDING),List.copyOf(text));
        return layout;
    }

    private static int block(List<Text> output,FontMetrics font,String value,int x,int y,int width,Color color){
        if(value.isEmpty())return y;
        var breaker=BreakIterator.getLineInstance(Locale.SIMPLIFIED_CHINESE);
        for(String paragraph:value.split("\\R",-1)){
            if(paragraph.isEmpty()){
                y+=LINE_HEIGHT;
                continue;
            }
            breaker.setText(paragraph);
            for(int start=0;start<paragraph.length();){
                int end=start;
                int used=0;
                while(end<paragraph.length()){
                    int codePoint=paragraph.codePointAt(end);
                    int advance=font.charWidth(codePoint);
                    if(end>start&&used+advance>width)break;
                    used+=advance;
                    end+=Character.charCount(codePoint);
                }
                if(end<paragraph.length()&&!breaker.isBoundary(end)){
                    int boundary=breaker.preceding(end);
                    if(boundary>start)end=boundary;
                }
                output.add(new Text(paragraph.substring(start,end).stripTrailing(),x,y,color));
                y+=LINE_HEIGHT;
                start=end;
            }
        }
        return y;
    }

    private static Color titleColor(GameItemInfo item){
        if(item.tooltip().skill())return new Color(111,213,255);
        if(item.tooltip().setItem())return new Color(126,232,39);
        return switch(item.rarity()){
            case 1 -> new Color(113,175,255);
            case 2 -> new Color(192,134,255);
            case 3 -> new Color(255,111,211);
            case 4 -> new Color(255,211,38);
            default -> NORMAL;
        };
    }

    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);
        if(item==null&&status.isEmpty())return;
        var card=card();
        var g=(Graphics2D)graphics.create();
        try{
            g.translate(MARGIN,MARGIN);
            g.setFont(getFont());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setColor(new Color(11,9,8,224));
            g.fillRect(0,0,card.width(),card.height());
            g.setColor(new Color(114,103,91));
            g.drawRect(0,0,card.width()-1,card.height()-1);
            var clip=g.getClipBounds();
            for(var line:card.text()){
                if(clip!=null&&(line.baseline()+3<clip.y||line.baseline()-LINE_HEIGHT>clip.y+clip.height))continue;
                g.setColor(Color.BLACK);
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
                    if(dx!=0||dy!=0)g.drawString(line.value(),line.x()+dx,line.baseline()+dy);
                }
                g.setColor(line.color());
                g.drawString(line.value(),line.x(),line.baseline());
            }
        }finally{g.dispose();}
    }

    @Override public Dimension getPreferredSize(){return new Dimension(CARD_WIDTH+2*MARGIN,card().height()+2*MARGIN);}
    @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(CARD_WIDTH+2*MARGIN,260);}
    @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return LINE_HEIGHT;}
    @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(LINE_HEIGHT,visible.height-LINE_HEIGHT);}
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override public boolean getScrollableTracksViewportHeight(){return false;}
}
