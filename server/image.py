"""Decode, orient, strip EXIF and normalize user images. No network or file input."""
import io, sys, warnings
from PIL import Image, ImageOps
Image.MAX_IMAGE_PIXELS = 20_000_000
warnings.simplefilter('error', Image.DecompressionBombWarning)
try:
    data = sys.stdin.buffer.read(3_000_001)
    if len(data) > 3_000_000: raise ValueError('size')
    image = Image.open(io.BytesIO(data))
    if image.format not in ('JPEG', 'PNG', 'WEBP'): raise ValueError('format')
    if image.width > 6000 or image.height > 6000: raise ValueError('dimensions')
    image.load()
    image = ImageOps.exif_transpose(image).convert('RGB')
    image.thumbnail((1920, 1920))
    clean = Image.new('RGB', image.size)
    clean.paste(image)
    clean.save(sys.stdout.buffer, format='JPEG', quality=88, optimize=True)
except Exception:
    sys.stderr.write('Invalid or oversized image')
    sys.exit(1)
