package gamblingden.tests;

import java.io.*;
import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.util.concurrent.ExecutorService;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import gamblingden.prizes.Prize;
import gamblingden.slots.Reel;

/** Optional integration check against the installed fr.jar; no game process or save is used. */
final class FastRendererChecks {
    interface PanelFactory { BaseCustomUIPanelPlugin create(Class<?> type) throws Exception; }

    static void run(PanelFactory factory) throws Exception {
        Class<?> stateType;
        try { stateType=Class.forName("com.genir.renderer.state.AppState"); }
        catch(ClassNotFoundException absent) {
            System.out.println("SKIP: Fast Rendering integration (fr.jar is not installed).");
            return;
        }
        Object state=stateType.getField("state").get(null);
        Object exec=stateType.getField("exec").get(state);
        Object detector=stateType.getField("stallDetector").get(state);
        Method enqueue=exec.getClass().getMethod("execute",Runnable.class);
        Method swap=exec.getClass().getMethod("swapFrames");
        Method update=detector.getClass().getMethod("update");
        Field stalled=detector.getClass().getDeclaredField("stallLastFrame");
        stalled.setAccessible(true);
        detector.getClass().getMethod("enableDetection").invoke(detector);
        Pbuffer buffer=new Pbuffer(1200,800,new PixelFormat(),null);
        try {
            enqueue.invoke(exec,(Runnable)()->{
                try { buffer.makeCurrent(); }
                catch(Exception e) { throw new RuntimeException(e); }
                GL11.glViewport(0,0,1200,800);
                GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,1200,0,800,-1,1);
                GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            });
            drain(exec,swap);

            // Apply the same GL11 call redirection as Fast Rendering, only to our panel
            // and drawing helper. Campaign/UI APIs still use the ordinary test mocks.
            Class<?> panelType=new BridgeLoader().loadClass("gamblingden.slots.SlotMachinePanel");
            BaseCustomUIPanelPlugin panel=factory.create(panelType);
            Method act=panelType.getDeclaredMethod("act",String.class); act.setAccessible(true);
            Field bank=panelType.getDeclaredField("reels"); bank.setAccessible(true);
            for(int reels=1;reels<=5;reels++) {
                act.invoke(panel,"reels:"+reels);
                for(int frame=0;frame<120;frame++) {
                    if(frame==60) {
                        int i=0;
                        for(Object value:(java.util.List<?>)bank.get(panel)) {
                            Reel reel=(Reel)value;
                            reel.spinning=true; reel.stopOn(Prize.values()[i++]); reel.snapToResult();
                        }
                    }
                    boolean clipped=frame%2==0;
                    enqueue.invoke(exec,(Runnable)()->{
                        if(clipped) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                        GL11.glScissor(15,20,1100,700);
                    });
                    panel.renderBelow(1f);
                    if(stalled.getBoolean(detector)) throw new AssertionError("Panel forced an asynchronous pipeline stall");
                    enqueue.invoke(exec,(Runnable)()->{
                        // Queries here run ON the render thread, after the panel's commands.
                        IntBuffer rect=BufferUtils.createIntBuffer(16);
                        GL11.glGetInteger(GL11.GL_SCISSOR_BOX,rect);
                        if(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)!=clipped || rect.get(0)!=15 || rect.get(1)!=20
                                || rect.get(2)!=1100 || rect.get(3)!=700) throw new AssertionError("FR clipping state leaked");
                        int error=GL11.glGetError();
                        if(error!=GL11.GL_NO_ERROR) throw new AssertionError("FR OpenGL error: "+error);
                    });
                    drain(exec,swap); update.invoke(detector);
                }
            }

            Class<?> pachinkoType=new BridgeLoader().loadClass("gamblingden.pachinko.PachinkoPanel");
            BaseCustomUIPanelPlugin pachinko=factory.create(pachinkoType);
            Method pachinkoAct=pachinkoType.getDeclaredMethod("act",String.class); pachinkoAct.setAccessible(true);
            gamblingden.economy.TokenBank.addTokens(1000);
            pachinkoAct.invoke(pachinko,"category:TOKENS");
            pachinkoAct.invoke(pachinko,"drop50");
            pachinkoAct.invoke(pachinko,"drop50");
            for(int frame=0;frame<600;frame++) {
                if(frame==300) pachinkoAct.invoke(pachinko,"skip");
                pachinko.advance(1f/60);
                boolean clipped=frame%2==0;
                enqueue.invoke(exec,(Runnable)()->{
                    if(clipped) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(15,20,1100,700);
                });
                pachinko.renderBelow(1f);
                if(stalled.getBoolean(detector)) throw new AssertionError("Pachinko forced an asynchronous pipeline stall");
                enqueue.invoke(exec,(Runnable)()->{
                    IntBuffer rect=BufferUtils.createIntBuffer(16);
                    GL11.glGetInteger(GL11.GL_SCISSOR_BOX,rect);
                    if(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)!=clipped || rect.get(0)!=15 || rect.get(1)!=20
                            || rect.get(2)!=1100 || rect.get(3)!=700) throw new AssertionError("Pachinko FR clipping leaked");
                    if(GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("Pachinko FR OpenGL error");
                });
                drain(exec,swap); update.invoke(detector);
            }

            // Positive control: the exact v0.4.2 query must reproduce the reported fatal
            // exception. Otherwise this harness is not actually testing the failing path.
            Class<?> bridge=Class.forName("com.genir.renderer.bridge.GL11");
            Method oldQuery=bridge.getMethod("glIsEnabled",int.class);
            boolean reproduced=false;
            for(int frame=0;frame<121;frame++) {
                try { oldQuery.invoke(null,GL11.GL_SCISSOR_TEST); }
                catch(InvocationTargetException e) {
                    if(!"Asynchronous pipeline stall".equals(e.getCause().getMessage())) throw e;
                    reproduced=true; break;
                }
                update.invoke(detector);
            }
            if(!reproduced) throw new AssertionError("Old crash condition was not reproduced");
            System.out.println("PASS: Fast Rendering bridge: 1200 panel frames (600 Slots + 600 Pachinko) without stalls or clipping leaks; v0.4.2 crash reproduced by control.");
        } finally {
            try {
                enqueue.invoke(exec,(Runnable)()->{
                    try { buffer.releaseContext(); }
                    catch(Exception e) { throw new RuntimeException(e); }
                });
                drain(exec,swap);
            } finally {
                Field worker=exec.getClass().getDeclaredField("execActual"); worker.setAccessible(true);
                ((ExecutorService)worker.get(exec)).shutdown();
                buffer.destroy();
            }
        }
    }

    private static void drain(Object exec, Method swap) throws Exception {
        // Submit this frame, then wait for it through the non-stalling frame boundary.
        swap.invoke(exec); swap.invoke(exec);
    }

    private static final class BridgeLoader extends ClassLoader {
        BridgeLoader() { super(FastRendererChecks.class.getClassLoader()); }

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if(!name.equals("gamblingden.slots.SlotMachinePanel") && !name.startsWith("gamblingden.slots.SlotMachinePanel$")
                    && !name.startsWith("gamblingden.pachinko.")
                    && !name.equals("gamblingden.ui.GLDraw")) return super.loadClass(name,resolve);
            Class<?> loaded=findLoadedClass(name);
            if(loaded==null) {
                try(InputStream source=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                    if(source==null) throw new ClassNotFoundException(name);
                    byte[] bytes=redirect(source.readAllBytes());
                    loaded=defineClass(name,bytes,0,bytes.length);
                } catch(IOException e) { throw new ClassNotFoundException(name,e); }
            }
            if(resolve) resolveClass(loaded);
            return loaded;
        }

        private static byte[] redirect(byte[] original) throws IOException {
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(original));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(in.readInt()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort());
            int count=in.readUnsignedShort(); out.writeShort(count);
            for(int i=1;i<count;i++) {
                int tag=in.readUnsignedByte(); out.writeByte(tag);
                if(tag==1) {
                    String value=in.readUTF();
                    out.writeUTF(value.equals("org/lwjgl/opengl/GL11") ? "com/genir/renderer/bridge/GL11" : value);
                    continue;
                }
                int size=switch(tag) {
                    case 3,4,9,10,11,12,17,18 -> 4;
                    case 5,6 -> 8;
                    case 7,8,16,19,20 -> 2;
                    case 15 -> 3;
                    default -> throw new IOException("Unknown constant pool tag: "+tag);
                };
                out.write(in.readNBytes(size));
                if(tag==5 || tag==6) i++;
            }
            out.write(in.readAllBytes());
            return bytes.toByteArray();
        }
    }
}
