"""Preserve the editor's logical canvas on the pre-event, PoseStack client API."""
import re

def adapt_client(text, name):
    text = re.sub(r'import net\.minecraft\.client\.input\.[^;]+;', '', text)
    text = text.replace('.pushMatrix()', '.pushPose()').replace('.popMatrix()', '.popPose()')
    text = re.sub(r'\.pose\(\)\.translate\(([^;]+)\);', r'.pose().translate(\1, 0);', text)
    text = re.sub(r'\.pose\(\)\.scale\(([^;]+?)\)', r'.pose().scale(\1, 1)', text)
    text = text.replace('net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,', '')
    text = text.replace('renderContents(', 'renderWidget(').replace('setTextShadow(false);', '')
    text = re.sub(r'\b(graphics|g)\.setTooltipForNextFrame\(', r'LegacyTooltips.queue(\1,', text)
    text = text.replace('KeyMapping.Category.register(new ResourceLocation("guishop", "adminshop"))', '"key.categories.guishop.adminshop"')
    text = re.sub(r'protected void init\(\)\s*\{', 'protected void init() { font=WorkbenchTheme.font();', text)
    text = re.sub(r'keyPressed\((?:net\.minecraft\.client\.input\.)?KeyEvent event\)', 'keyPressed(int key, int scanCode, int modifiers)', text)
    text = text.replace('event.key()', 'key').replace('super.keyPressed(event)', 'super.keyPressed(key,scanCode,modifiers)')
    if name in ('AdminScreen.java','IconPickerScreen.java'):
        text = text.replace('mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy)', 'mouseDragged(double mouseX, double mouseY, int button, double dx, double dy)')
        text = text.replace('event.x()', 'mouseX').replace('event.y()', 'mouseY').replace('super.mouseDragged(event, dx, dy)', 'super.mouseDragged(mouseX,mouseY,button,dx,dy)')
    if name == 'WorkbenchScreen.java':
        text = text.replace('super(net.minecraft.client.Minecraft.getInstance(), WorkbenchTheme.font(), Component.literal(title));', 'super(Component.literal(title)); this.font=WorkbenchTheme.font();')
        text = re.sub(r'    private MouseButtonEvent logical\(.*?\n    }\n', '', text, flags=re.S)
        start=text.index('    @Override public boolean mouseClicked(')
        end=text.index('    @Override public boolean mouseScrolled(',start)
        text=text[:start]+'''    @Override public boolean mouseClicked(double x,double y,int button) {
        geometry(); double lx=(x-originX)/scale-contentOffsetX(),ly=(y-originY)/scale;
        return scroller()!=null&&scroller().click(lx,ly,button)||click(lx,ly,button)||super.mouseClicked(lx,ly,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(scroller()!=null&&scroller().dragging){scroller().dragging=false;return true;}
        double lx=(x-originX)/scale-contentOffsetX(),ly=(y-originY)/scale;
        return release(lx,ly,button)||super.mouseReleased(lx,ly,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(scroller()!=null&&scroller().dragging){scroller().drag(logicalY(y));return true;}
        return super.mouseDragged((x-originX)/scale-contentOffsetX(),(y-originY)/scale,button,dx/scale,dy/scale);
    }
'''+text[end:]
        text=text.replace('graphics.pose().popPose();','graphics.pose().popPose(); LegacyTooltips.flush();')
        text=text.replace('    protected abstract void paint(', '    public void renderBackground(GuiGraphics graphics,int x,int y,float delta) {}\n    protected abstract void paint(')
    if name == 'WorkbenchField.java':
        start=text.index('    private MouseButtonEvent inset(')
        text=text[:start]+'''    @Override public boolean mouseClicked(double x,double y,int button) {
        if(!isMouseOver(x,y))return false;
        return super.mouseClicked(Math.max(getX()+0.5,x-6),y,button);
    }
    @Override protected void onDrag(double x,double y,double dx,double dy) { super.onDrag(Math.max(getX()+0.5,x-6),y,dx,dy); }
}
'''
    if name == 'EditorFontAccess.java':
        text=text.replace('@Accessor("provider") Font.Provider', '@Accessor("fonts") java.util.function.Function<net.minecraft.resources.ResourceLocation,net.minecraft.client.gui.font.FontSet>')
    if name == 'WorkbenchTheme.java':
        text=text.replace('import net.minecraft.network.chat.FontDescription;', '')
        start=text.index('            var ui =')
        end=text.index('\n        }',start)
        text=text[:start]+'''            var ui = new ResourceLocation("guishop", "ui");
            editorFont = new Font(id -> original.apply(id.equals(net.minecraft.network.chat.Style.DEFAULT_FONT) ? ui : id), false);'''+text[end:]
    return text
