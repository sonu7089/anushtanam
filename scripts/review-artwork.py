"""Render source/derivative comparisons at actual placement pixels for visual QA."""
import importlib.util
import json
import math
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('exporter', ROOT/'scripts/generate-artwork.py')
sys.dont_write_bytecode = True
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)
design = ROOT/'frontend/android/design/artwork'
out = ROOT/'build/step10/comparisons'
out.mkdir(parents=True, exist_ok=True)
manifest = json.loads((design/'manifest.json').read_text())
rows = []

def slot(image, size, crop, background):
    image = image.convert('RGBA')
    if crop:
        image = ImageOps.fit(image, size, Image.Resampling.LANCZOS, centering=(1, .5))
    else:
        image.thumbnail(size, Image.Resampling.LANCZOS)
    canvas = Image.new('RGBA', size, background)
    canvas.alpha_composite(image, (size[0]-image.width, (size[1]-image.height)//2))
    return canvas.convert('RGB')

for density in [1, 3, 4]:
    for asset in manifest['assets']:
        master = Image.open(design/'masters'/asset['file'])
        square_slots = {'GOOGLE':[20], 'DIYA':[72,96,104], 'VESSEL':[48,56,76],
                        'BOOK':[56,72,76,208], 'MALA':[76,208], 'EMBLEM':[48,96,140,160]}
        placements = ([(s,s,'image') for s in square_slots[asset['id']]] if asset['id'] in square_slots
            else [(88,120,'image'),(132,120,'image')] if asset['id']=='JASMINE'
            else [(72,72,'thumb'),(360,248,'image'),(600,260,'image')])
        if asset['id']=='MESSAGES': placements += [(320,124,'banner')]
        for w,h,usage in placements:
            size=(w*density,h*density)
            crop=not asset['alpha']
            usage_spec=asset['usages'][usage]
            candidates=[]
            for edge in usage_spec['edges']:
                path=ROOT/'frontend/android/src/main/res/drawable-nodpi'/f"art_{Path(asset['file']).stem}_{usage}_{edge}.webp"
                im=Image.open(path)
                candidates.append((im,path))
            largest=candidates[-1][0]
            scale=(max if crop else min)(size[0]/largest.width,size[1]/largest.height)
            chosen=next(((im,p) for im,p in candidates if im.width>=math.ceil(largest.width*scale) and im.height>=math.ceil(largest.height*scale)),candidates[-1])
            for theme,bg in [('light','#ffffff'),('dark','#171b18')]:
                original=slot(master.copy(),size,crop,bg)
                derivative=slot(chosen[0].copy(),size,crop,bg)
                diff=ImageStat.Stat(ImageChops.difference(original,derivative))
                error=sum(diff.mean)/3
                label=f"{asset['id']}-{usage}-{w}x{h}-{density}x-{theme}"
                canvas=Image.new('RGB',(size[0]*2,size[1]+30),'#dddddd')
                canvas.paste(original,(0,30)); canvas.paste(derivative,(size[0],30))
                ImageDraw.Draw(canvas).text((5,5),f'{label} | master left / derivative right',fill='black')
                canvas.save(out/(label+'.png'))
                rows.append(dict(placement=label,resource=chosen[1].name,meanAbsoluteError=round(error,3),
                                 sourceLimited=scale>1))
(out/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
print(f'{len(rows)} comparisons; max mean channel error {max(r["meanAbsoluteError"] for r in rows):.3f}/255')
