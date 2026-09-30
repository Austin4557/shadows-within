from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import random

W, H = 680, 768
random.seed(7319)
im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(im, "RGBA")

# Tall, mostly-shadowed humanoid cutout. Intentionally non-graphic.
d.ellipse((205, 54, 475, 316), fill=(18, 18, 20, 242))
d.ellipse((250, 94, 430, 282), fill=(151, 148, 145, 220))
d.polygon([(220,250),(460,250),(575,760),(105,760)], fill=(9,9,12,246))
# Hair/shadow curtains.
for _ in range(80):
    x=random.randint(190,485); y=random.randint(45,250)
    d.line((x,y,x+random.randint(-55,55),random.randint(350,650)), fill=(4,4,6,random.randint(110,220)), width=random.randint(3,10))
# Recessed eyes, tiny pale catches.
for cx in (300,380):
    d.ellipse((cx-28,166,cx+28,218), fill=(10,10,12,235))
    d.ellipse((cx-7,184,cx+7,198), fill=(205,205,198,220))
# Face shading and almost unreadable mouth.
d.ellipse((286,215,394,274), fill=(60,55,55,70))
d.line((310,251,370,254), fill=(35,30,32,180), width=5)
# Long shoulders/arms vanish into shadow.
d.polygon([(220,285),(115,410),(72,735),(155,748),(275,385)], fill=(8,8,10,235))
d.polygon([(460,285),(565,410),(608,735),(525,748),(405,385)], fill=(8,8,10,235))

# Soft edge treatment so it sits in darkness rather than looking like a sticker.
alpha=im.getchannel("A").filter(ImageFilter.GaussianBlur(2.2))
im.putalpha(alpha)
out=Path("src/main/resources/assets/shadows_within/textures/gui/watcher.png")
out.parent.mkdir(parents=True, exist_ok=True)
im.save(out, optimize=True)
print(out)
