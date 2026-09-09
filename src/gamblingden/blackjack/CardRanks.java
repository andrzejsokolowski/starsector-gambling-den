package gamblingden.blackjack;

import java.awt.Color;
import org.lwjgl.opengl.GL11;

/** Plain vector ranks, without the shadow baked into Starsector's small UI font. */
final class CardRanks {
    private CardRanks() { }
    // Paths use a 10 x 16 grid, with y measured down from the top.
    private static String path(char rank) {
        return switch (rank) {
            case 'A' -> "0,16 5,0 10,16;2,10 8,10";
            case '2' -> "0,3 2,0 8,0 10,3 10,6 0,16 10,16";
            case '3' -> "0,0 10,0 6,7 10,10 10,13 7,16 0,16;4,7 6,7";
            case '4' -> "8,16 8,0 0,11 10,11";
            case '5' -> "10,0 0,0 0,7 7,7 10,10 10,13 7,16 0,16";
            case '6' -> "9,0 4,0 0,5 0,13 3,16 7,16 10,13 10,10 7,7 0,7";
            case '7' -> "0,0 10,0 3,16";
            case '8' -> "3,0 7,0 10,3 10,5 0,11 0,13 3,16 7,16 10,13 10,11 0,5 0,3 3,0";
            case '9' -> "10,9 3,9 0,6 0,3 3,0 7,0 10,3 10,12 6,16 1,16";
            case '1' -> "1,3 5,0 5,16;1,16 9,16";
            case '0' -> "3,0 7,0 10,3 10,13 7,16 3,16 0,13 0,3 3,0";
            case 'J' -> "2,0 10,0;8,0 8,13 5,16 2,16 0,13 0,11";
            case 'Q' -> "3,0 7,0 10,3 10,12 7,15 3,15 0,12 0,3 3,0;6,11 11,17";
            case 'K' -> "0,0 0,16;10,0 0,9 10,16";
            default -> "";
        };
    }
    private static final float[][][] GLYPHS = new float[128][][];
    static {
        for (char c : "A2345678910JQK".toCharArray()) {
            String[] paths = path(c).split(";");
            GLYPHS[c] = new float[paths.length][];
            for (int i=0;i<paths.length;i++) {
                String[] values=paths[i].replace(' ', ',').split(",");
                float[] points=new float[values.length];
                for(int j=0;j<values.length;j++) points[j]=Float.parseFloat(values[j]);
                GLYPHS[c][i]=points;
            }
        }
    }
    static void draw(String rank, float left, float top, float height, Color ink, float alpha) {
        float sy=height/16f, sx=rank.length()==2?sy*.68f:sy;
        GL11.glColor4f(ink.getRed()/255f,ink.getGreen()/255f,ink.getBlue()/255f,alpha);
        GL11.glBegin(GL11.GL_QUADS);
        for(int i=0;i<rank.length();i++) {
            char c=rank.charAt(i);
            if(c>=GLYPHS.length || GLYPHS[c]==null) continue;
            for(float[] points:GLYPHS[c]) for(int j=2;j<points.length;j+=2) {
                float ax=left+i*14*sx+points[j-2]*sx, ay=top-points[j-1]*sy;
                float bx=left+i*14*sx+points[j]*sx, by=top-points[j+1]*sy;
                float dx=bx-ax, dy=by-ay, length=(float)Math.sqrt(dx*dx+dy*dy);
                float radius=height*.06f, nx=-dy/length*radius, ny=dx/length*radius;
                // Square caps meet at the path joints, without font filtering or shadows.
                float ex=dx/length*radius, ey=dy/length*radius;
                GL11.glVertex2f(ax-ex+nx,ay-ey+ny); GL11.glVertex2f(bx+ex+nx,by+ey+ny);
                GL11.glVertex2f(bx+ex-nx,by+ey-ny); GL11.glVertex2f(ax-ex-nx,ay-ey-ny);
            }
        }
        GL11.glEnd();
    }
}
