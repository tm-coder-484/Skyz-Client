package net.skyz.client.util;

import net.minecraft.client.gui.DrawContext;
import java.util.Random;

/**
 * Floating particle motes matching the HTML canvas particle system.
 */
public class ParticleSystem {

    private static final int COUNT = 80;
    private static final Random RNG = new Random();

    private final float[] x, y, vx, vy, life, maxLife, r;
    private final boolean[] shimmer;
    private int screenW, screenH;
    private boolean enabled = true;

    public ParticleSystem() {
        x=new float[COUNT]; y=new float[COUNT];
        vx=new float[COUNT]; vy=new float[COUNT];
        life=new float[COUNT]; maxLife=new float[COUNT];
        r=new float[COUNT]; shimmer=new boolean[COUNT];
    }

    public void resize(int w, int h) {
        screenW=w; screenH=h;
        for (int i=0;i<COUNT;i++) spawnMote(i,true);
    }

    public void setEnabled(boolean on) { enabled=on; }

    private void spawnMote(int i, boolean randomY) {
        x[i]=RNG.nextFloat()*screenW;
        y[i]=randomY?RNG.nextFloat()*screenH:screenH+4;
        vx[i]=(RNG.nextFloat()-0.5f)*0.12f;
        vy[i]=-(0.10f+RNG.nextFloat()*0.25f);
        life[i]=0; maxLife[i]=400+RNG.nextFloat()*700;
        r[i]=0.4f+RNG.nextFloat()*1.4f;
        shimmer[i]=RNG.nextFloat()<0.40f;
    }

    public void tick(DrawContext ctx, float delta) {
        if (!enabled || screenW==0) return;
        for (int i=0;i<COUNT;i++) {
            x[i]+=vx[i]*delta; y[i]+=vy[i]*delta; life[i]+=delta;
            if (life[i]>=maxLife[i]||y[i]<-4) { spawnMote(i,false); continue; }
            float t=life[i]/maxLife[i];
            float alpha=t<0.1f?t/0.1f:t>0.8f?(1f-t)/0.2f:1f;
            int px=(int)x[i], py=(int)y[i];
            if (shimmer[i]) {
                int baseA=(int)(alpha*0.45f*255);
                for (int ring=(int)(r[i]*3);ring>=1;ring--) {
                    int a=Math.max(0,baseA-ring*20);
                    ctx.fill(px-ring,py-1,px+ring,py+1,(a<<24)|0xC8F0FF);
                    ctx.fill(px-1,py-ring,px+1,py+ring,(a<<24)|0xC8F0FF);
                }
                int cA=Math.min(255,(int)(alpha*0.45f*255));
                ctx.fill(px-1,py-1,px+1,py+1,(cA<<24)|0xFFFFFF);
            } else {
                int a=(int)(alpha*0.28f*255);
                int ir=Math.max(1,(int)r[i]);
                ctx.fill(px-ir,py-ir,px+ir,py+ir,(a<<24)|0xB4E1FF);
            }
        }
    }
}
