#!/usr/bin/env python3
"""Create transparent wide-capsule spines from REAL in-repo logo images."""
import json
import math
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/assets/system_logos_png"
OUT = ROOT / "tools/console_spines/output"
PNG = OUT / "png"
W, H = 240, 720
OPACITY = 0.24

def accent(key):
    if key in ("gb","gba","gbc","gc","n64","nds","n3ds","nes","snes","sfc","famicom","wii","wiiu"): return (30,62,151)
    if key in ("switch","virtualboy"): return (173,21,41)
    if key.startswith("ps"): return (31,72,140)
    if key.startswith("xbox"): return (30,131,55)
    if key in ("saturn","dreamcast","megadrive","genesis","segacd","megacd","mastersystem","gamegear","sega32x"): return (31,81,120)
    if key.startswith("atari"): return (148,39,43)
    if key in ("pcengine","pcenginecd","tg16","tg-cd"): return (103,61,119)
    if key in ("android","androidapps"): return (45,123,65)
    return (64,62,82)

def load_logo(path):
    im = Image.open(path).convert("RGBA")
    if max(im.size)>4000: im.thumbnail((4000,4000),Image.Resampling.LANCZOS)
    bbox = im.getchannel("A").point(lambda n: 255 if n > 8 else 0).getbbox()
    if not bbox: raise ValueError("Empty logo: "+path.name)
    im = im.crop(bbox)
    # Read bottom-to-top like the GBA spine shown in the reference screenshot.
    if im.width>im.height*1.08:
        im = im.rotate(90,expand=True,resample=Image.Resampling.BICUBIC)
    im.thumbnail((166,626),Image.Resampling.LANCZOS)
    return im

def panel(key):
    rgb = accent(key)
    im=Image.new("RGBA",(W,H),(0,0,0,0))
    mask=Image.new("L",(W,H),0)
    ImageDraw.Draw(mask).rounded_rectangle((3,3,W-4,H-4),radius=16,fill=255)
    pixels=Image.new("RGBA",(W,H),(0,0,0,0))
    p=pixels.load()
    for y in range(H):
        wave=.5+.5*math.cos(math.pi*y/(H-1))
        a=int(255*(OPACITY-.035+.07*wave))
        for x in range(W):
            shade=.22+.15*(1-x/(W-1))
            p[x,y]=(int(rgb[0]*shade),int(rgb[1]*shade),int(rgb[2]*shade),a)
    pixels.putalpha(Image.composite(pixels.getchannel("A"),Image.new("L",(W,H),0),mask))
    im.alpha_composite(pixels)
    d=ImageDraw.Draw(im)
    d.rounded_rectangle((3,3,W-4,H-4),radius=16,outline=(210,219,246,75),width=3)
    d.line([(10,20),(10,H-20)],fill=(*rgb,112),width=4)
    d.line([(W-8,18),(W-8,H-18)],fill=(217,221,246,50),width=2)
    return im

def font(size):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf","/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"):
        if Path(p).exists(): return ImageFont.truetype(p,size)
    return ImageFont.load_default()

def preview(items):
    cols=8; cw=174; ch=385
    rows=math.ceil(len(items)/cols)
    sheet=Image.new("RGB",(cols*cw,rows*ch+75),(17,19,25))
    d=ImageDraw.Draw(sheet)
    d.text((18,16),"VOLTCAP  /  REAL LOGO SPINES",font=font(25),fill=(235,239,245))
    d.text((18,46),"Existing repository PNG logos, original colors, 24% backdrop opacity",font=font(13),fill=(160,174,190))
    for i,item in enumerate(items):
        x=(i%cols)*cw+28; y=(i//cols)*ch+82
        for by in range(0,320,20):
            for bx in range(0,108,20):
                c=(90,93,99) if ((by+bx)//20)%2 else (120,123,129)
                d.rectangle((x+bx,y+by,x+bx+19,y+by+19),fill=c)
        png=Image.open(PNG/item["filename"]).convert("RGBA")
        png.thumbnail((108,320),Image.Resampling.LANCZOS)
        sheet.paste(png,(x+(108-png.width)//2,y+(320-png.height)//2),png)
        d.text((x-5,y+332),item["platform"],font=font(15),fill=(230,235,242))
    sheet.save(OUT/"preview.png")

def main():
    if not SRC.is_dir(): raise RuntimeError("Source logos not found: "+str(SRC))
    PNG.mkdir(parents=True,exist_ok=True)
    items=[]
    for src in sorted(SRC.glob("*.png")):
        key=src.stem
        logo=load_logo(src)
        canvas=panel(key)
        x=(W-logo.width)//2; y=(H-logo.height)//2
        shadow=Image.new("RGBA",logo.size,(0,0,0,0))
        shadow.putalpha(logo.getchannel("A").filter(ImageFilter.GaussianBlur(5)).point(lambda v:int(v*.58)))
        canvas.alpha_composite(shadow,(x+2,y+2))
        canvas.alpha_composite(logo,(x,y))
        assert canvas.getpixel((0,0))[3]==0
        target=PNG/(key+".png")
        canvas.save(target,optimize=True)
        items.append({"platform":key,"filename":target.name,
          "logo_source":str(src.relative_to(ROOT)),"output_size":[W,H],
          "original_logo_colors":True,"backdrop_opacity":OPACITY})
    if len(items)<65: raise RuntimeError("Not all 65 expected source PNG logos were found")
    preview(items)
    (OUT/"manifest.json").write_text(json.dumps({"count":len(items),
       "source_policy":"Only existing project PNGs. No AI-generated wordmarks.",
       "canvas":[W,H],"background_opacity":OPACITY,"platforms":items},indent=2),encoding="utf-8")
    (OUT/"README.md").write_text(
       "# VoltCap real-logo console spines\n\n"
       +str(len(items))+" individual 240x720 RGBA PNG files; equivalent to 120x360 at 2x scale.\n\n"
       "Use as an OPTIONAL left-edge overlay for wide capsules. Default OFF. "
       "Panel is about 24% opaque and the authentic-source logo pixels are not faded "
       "or recolored. No guessed logo text is rendered by this generator.\n\n"
       "The source is app/src/main/assets/system_logos_png. "
       "Source files have not been individually manufacturer-authenticated; if a "
       "source logo itself is wrong, replace it and regenerate. "
       "Any system not represented in the source must be reviewed, not invented.\n",encoding="utf-8")
    with zipfile.ZipFile(OUT/"VoltCap_Real_Logo_Spines.zip","w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for f in sorted(PNG.glob("*.png")): z.write(f,"png/"+f.name)
        for n in ("README.md","manifest.json","preview.png"): z.write(OUT/n,n)
    print("SUCCESS:",len(items),"actual-source PNGs; zip at",OUT/"VoltCap_Real_Logo_Spines.zip")

if __name__=="__main__": main()
