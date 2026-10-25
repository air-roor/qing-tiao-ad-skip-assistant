"""Illustrated design review only; not an Android screenshot or device test.
Run with Python + Pillow on Windows. No device data or external artwork.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math

ROOT = Path(__file__).resolve().parents[1]
S=2
im=Image.new('RGB',(1240*S,1100*S),'#080C13')
d=ImageDraw.Draw(im)
BG='#090D14'; SURFACE='#121A26'; INK='#EDF3FA'; MUTED='#97A9BC'; CYAN='#6DE5ED'; SOFT='#17313D'; LINE='#29384A'
def box(x,y,w,h,fill=SURFACE,r=18,line=LINE):
    d.rounded_rectangle((x*S,y*S,(x+w)*S,(y+h)*S),radius=r*S,fill=fill,outline=line,width=S)
def font(size,bold=False,mono=False):
    return ImageFont.truetype('C:/Windows/Fonts/'+('consola.ttf' if mono else 'msyhbd.ttc' if bold else 'msyh.ttc'),int(size*S))
def txt(x,y,v,size=14,color=INK,bold=False,mono=False,center=False):
    d.text((x*S,y*S),v,font=font(size,bold,mono),fill=color,anchor='mt' if center else 'lt')
def ln(x,y,x2,y2,color=LINE,width=1):
    d.line((x*S,y*S,x2*S,y2*S),fill=color,width=width*S)
def circle(cx,cy,r,color,width=1,fill=None):
    d.ellipse(((cx-r)*S,(cy-r)*S,(cx+r)*S,(cy+r)*S),outline=color,width=width*S,fill=fill)
def bolt(cx,cy,size=25,color=CYAN):
    pts=[(cx+size*.15,cy-size*.55),(cx-size*.5,cy+size*.08),(cx-size*.05,cy+size*.08),(cx-size*.15,cy+size*.55),(cx+size*.5,cy-size*.14),(cx+size*.02,cy-size*.14)]
    d.polygon([(x*S,y*S) for x,y in pts],fill=color)
def icon(cx,cy,name,color=CYAN):
    if name=='bolt':bolt(cx,cy,19,color)
    elif name=='apps':
        for dx in [-7,3]:
            for dy in [-7,3]:box(cx+dx,cy+dy,6,6,BG,1,color)
    elif name=='user':
        circle(cx,cy-4,4,color);d.arc(((cx-8)*S,(cy+2)*S,(cx+8)*S,(cy+15)*S),190,350,fill=color,width=S)
    elif name=='play':
        pts=[(cx-5,cy-8),(cx+7,cy),(cx-5,cy+8),(cx-5,cy-8)];d.line([(a*S,b*S) for a,b in pts],fill=color,width=S)
    elif name=='chart':
        ln(cx-7,cy+7,cx+8,cy+7,color);ln(cx-7,cy-7,cx-7,cy+7,color)
        for dx,h in [(-3,5),(2,9),(7,13)]:ln(cx+dx,cy+3,cx+dx,cy+3-h,color,2)
    elif name=='share':
        for dx,dy in [(-6,0),(6,-7),(6,7)]:circle(cx+dx,cy+dy,2,color)
        ln(cx-4,cy-1,cx+4,cy-6,color);ln(cx-4,cy+1,cx+4,cy+6,color)
    else:
        pts=[(cx,cy-9),(cx+8,cy-6),(cx+7,cy+3),(cx,cy+9),(cx-7,cy+3),(cx-8,cy-6),(cx,cy-9)]
        d.line([(a*S,b*S) for a,b in pts],fill=color,width=S);ln(cx-4,cy,cx-1,cy+3,color);ln(cx-1,cy+3,cx+4,cy-3,color)
def button(x,y,w,label,filled=False):
    box(x,y,w,48,CYAN if filled else SURFACE,12,CYAN if filled else LINE)
    txt(x+w/2,y+14,label,14,BG if filled else INK,True,center=True)
def action(x,y,title,sub,kind='Q',divider=True):
    box(x,y+4,38,38,SOFT,11,SOFT);icon(x+19,y+23,{'ϟ':'bolt','▷':'play','⌁':'chart','↗':'share'}.get(kind,'shield'))
    txt(x+51,y+6,title,14,INK,True);txt(x+51,y+29,sub,10,MUTED);txt(x+286,y+14,'›',22,MUTED)
    if divider:ln(x,y+60,x+300,y+60)
def nav(x,y,selected):
    box(x,y,318,67,SURFACE,19)
    for i,(name,glyph) in enumerate([('首页','ϟ'),('应用','⊞'),('我的','○')]):
        xx=x+5+i*103
        if i==selected:box(xx,y+5,101,57,SOFT,14)
        icon(xx+50,y+22,['bolt','apps','user'][i],CYAN if i==selected else MUTED)
        txt(xx+50,y+39,name,10,CYAN if i==selected else MUTED,center=True)
def phone(x,title,sub,index):
    box(x,158,350,870,BG,30,'#344354');txt(x+23,179,'09:41',11,INK,mono=True);txt(x+286,180,'•••',11,MUTED)
    if index:
        txt(x+20,224,title,25,INK,True);txt(x+20,263,sub,12,MUTED)
    nav(x+16,945,index)

txt(48,42,'轻跳',34,INK,True);txt(144,54,'GRAPHITE / CYAN',13,CYAN,mono=True)
txt(48,99,'新版视觉预览 · 示意数据与图标，仅用于设计评审，非手机实拍',14,MUTED)
xs=[48,444,840]
for i,(t,sub) in enumerate([('',''),('应用管理','只在你选择的应用中，轻轻跳过。'),('我的轻跳','专注当下，让打扰留在身后。')]):phone(xs[i],t,sub,i)
x=xs[0]+20
box(x,217,40,40,SOFT,13);bolt(x+20,237,24);txt(x+52,215,'轻跳',22,INK,True);txt(x+52,245,'LESS NOISE. MORE FOCUS.',8,MUTED,mono=True)
box(x+240,224,70,26,SOFT,8);txt(x+275,232,'免费公测',10,CYAN,center=True)
# Hero mirrors the native hierarchy; the real app derives every status from local state.
box(x,279,310,337,'#12212D',23,'#34505F');txt(x+18,297,'AUTO SKIP',10,CYAN,mono=True)
txt(x+217,297,'● 运行就绪',10,CYAN)
txt(x+155,327,'轻跳正在守候',24,INK,True,center=True);txt(x+155,364,'打开所选应用，自动寻找跳过按钮。',11,MUTED,center=True)
cx=x+155;cy=468
for r in range(93,46,-1):
    # Concentric opaque tints approximate the native radial glow without blur.
    k=(94-r)/48;circle(cx,cy,r,tuple(int(a+(b-a)*k) for a,b in zip((18,33,45),(22,57,68))))
for r in [76,62]:circle(cx,cy,r,'#34505F')
for i in range(60):
    a=math.radians(i*6-90);r=82 if i%5==0 else 85
    ln(cx+math.cos(a)*r,cy+math.sin(a)*r,cx+math.cos(a)*88,cy+math.sin(a)*88,'#638499' if i%5==0 else '#304454')
d.arc(((cx-76)*S,(cy-76)*S,(cx+76)*S,(cy+76)*S),-65,168,fill=CYAN,width=3*S)
d.arc(((cx-62)*S,(cy-62)*S,(cx+62)*S,(cy+62)*S),74,150,fill='#818CF8',width=2*S)
circle(cx,cy,45,'#426374',fill='#142C39');bolt(cx,cy-4,41);txt(cx,cy+26,'QINGTIAO',7,MUTED,mono=True,center=True)
ln(x+10,cy,x+52,cy,'#426374');ln(x+258,cy,x+300,cy,'#426374');txt(x+15,cy-15,'LOCAL',6,MUTED,mono=True);txt(x+270,cy-15,'READY',6,MUTED,mono=True)
ln(x+18,566,x+292,566);txt(x+18,580,'自动跳过',14,INK,True);txt(x+18,601,'仅在你选择的应用中生效',10,MUTED)
box(x+252,581,40,21,SOFT,11,SOFT);circle(x+283,591,12,CYAN,fill=CYAN)
for off,label,value,glyph in [(0,'点击请求','128','ϟ'),(161,'已选应用','12','⊞')]:
    box(x+off,630,149,92,SURFACE,18);txt(x+off+15,645,label,11,MUTED);icon(x+off+127,651,'bolt' if off==0 else 'apps');txt(x+off+15,670,value,29,INK,mono=True)
txt(x,734,'点击请求包含模拟测试，不等于已确认广告消失。',9,MUTED)
button(x,761,310,'体验自动跳过',True);icon(x+76,785,'play',BG);txt(x,834,'运行配置',15,INK,True)
box(x,864,310,68,SURFACE,18);action(x+10,873,'无障碍服务','已连接 · 自动跳过已就绪','Q',False)
# App management demo data intentionally use generic invented names, not a phone inventory.
x=xs[1]+20
box(x,298,310,49,SURFACE,14);txt(x+15,315,'搜索应用名称或包名',12,MUTED)
button(x,360,150,'全部应用',True);button(x+160,360,150,'已选择')
txt(x,425,'已选择 12 个  ·  已保存',11,MUTED)
for i,(name,pkg,color) in enumerate([('音乐','example.music','#7A83EF'),('阅读','example.reader','#4B9C9B'),('资讯','example.news','#C48364'),('视频','example.video','#7B79AF'),('生活','example.life','#4F8CAD')]):
    y=455+i*79;chosen=i in [0,1,3];box(x,y,310,70,SOFT if chosen else SURFACE,15,CYAN if chosen else LINE)
    box(x+12,y+15,38,38,color,11,color);txt(x+31,y+25,name[0],15,INK,True,center=True)
    txt(x+63,y+16,name,14,INK,True);txt(x+63,y+40,pkg,10,MUTED,mono=True)
    box(x+272,y+25,20,20,CYAN if chosen else SURFACE,5,CYAN if chosen else MUTED)
    if chosen:ln(x+278,y+35,x+281,y+38,BG,2);ln(x+281,y+38,x+287,y+30,BG,2)
button(x,869,128,'取消修改');button(x+138,869,172,'已保存 12 个',True)
# Profile.
x=xs[2]+20
box(x,299,310,185,'#12212D',22,'#34505F');box(x+18,317,144,26,SOFT,8);txt(x+27,325,'免费公测 · 无需登录',10,CYAN)
txt(x+18,361,'一起让轻跳更好用',20,INK,True);txt(x+18,398,'遇到漏跳或服务关闭？告诉我们。',11,MUTED);button(x+18,422,274,'填写公测反馈',True)
txt(x,510,'使用偏好',15,INK,True);box(x,542,310,187,SURFACE,18)
action(x+10,553,'关闭广告弹窗','仅处理明确标注的广告关闭按钮','ϟ');action(x+10,614,'模拟广告测试','看看自动跳过是否正常','▷');action(x+10,675,'服务与后台运行','查看授权和后台设置建议','Q',False)
txt(x,753,'本机记录',15,INK,True);box(x,785,310,130,SURFACE,18);action(x+10,796,'查看最近诊断','服务状态与最近处理记录','⌁');action(x+10,858,'导出诊断','预览、编辑后再主动分享','↗',False)
txt(48,1061,'0.3.0-beta.5  /  原生 Android View · 静态矢量光效 · 无网络字体',12,MUTED)
im.resize((1240,1100),Image.Resampling.LANCZOS).save(ROOT/'docs/ui-tech-preview.png')
print('Wrote docs/ui-tech-preview.png (illustrated preview, not device evidence)')

