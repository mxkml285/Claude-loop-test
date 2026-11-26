// Cuts an app logo out of a flat icon with a grey/silver background (e.g. the IBO Player JPG icon).
// Flood fill from the border over low-saturation pixels marks the background; enclosed white parts
// (inside the logo) stay. Output: upscaled PNG with alpha, cropped to the logo.
// Usage: swift tools/cutout-logo.swift <in.jpg> <out.png> [scale=3]
import AppKit
import CoreGraphics

let args = CommandLine.arguments
guard args.count >= 3, let src = NSImage(contentsOfFile: args[1]),
      let cg = src.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    print("usage: cutout-logo.swift <in> <out.png> [scale]"); exit(1)
}
let scale = args.count > 3 ? Int(args[3])! : 3
let w = cg.width * scale, h = cg.height * scale
let cs = CGColorSpaceCreateDeviceRGB()
let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: cs,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
ctx.interpolationQuality = .high
ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
let px = ctx.data!.bindMemory(to: UInt8.self, capacity: w * h * 4)

func rgb(_ i: Int) -> (Int, Int, Int) { (Int(px[i * 4]), Int(px[i * 4 + 1]), Int(px[i * 4 + 2])) }
func isBg(_ i: Int) -> Bool {
    let (r, g, b) = rgb(i)
    let mx = max(r, g, b), mn = min(r, g, b)
    return mx - mn < 22 && mx > 110   // grey/silver, not the black triangle
}

// Flood fill from the border.
var bg = [Bool](repeating: false, count: w * h)
var stack: [Int] = []
for x in 0..<w { stack.append(x); stack.append((h - 1) * w + x) }
for y in 0..<h { stack.append(y * w); stack.append(y * w + w - 1) }
while let i = stack.popLast() {
    if bg[i] || !isBg(i) { continue }
    bg[i] = true
    let x = i % w, y = i / w
    if x > 0 { stack.append(i - 1) }
    if x < w - 1 { stack.append(i + 1) }
    if y > 0 { stack.append(i - w) }
    if y < h - 1 { stack.append(i + w) }
}

// Soft alpha: 3x3 box blur of the mask (anti-aliased edge).
var alpha = [UInt8](repeating: 0, count: w * h)
var minX = w, minY = h, maxX = 0, maxY = 0
for y in 0..<h {
    for x in 0..<w {
        var s = 0, n = 0
        for dy in -1...1 { for dx in -1...1 {
            let xx = x + dx, yy = y + dy
            if xx < 0 || yy < 0 || xx >= w || yy >= h { continue }
            s += bg[yy * w + xx] ? 0 : 255; n += 1
        } }
        let a = UInt8(s / n)
        alpha[y * w + x] = a
        if a > 8 { minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) }
    }
}

let cw = maxX - minX + 1, ch = maxY - minY + 1
let out = CGContext(data: nil, width: cw, height: ch, bitsPerComponent: 8, bytesPerRow: cw * 4, space: cs,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
let op = out.data!.bindMemory(to: UInt8.self, capacity: cw * ch * 4)
for y in 0..<ch {
    for x in 0..<cw {
        let si = (y + minY) * w + (x + minX), di = y * cw + x
        let a = Int(alpha[si])
        let (r, g, b) = rgb(si)
        op[di * 4] = UInt8(r * a / 255); op[di * 4 + 1] = UInt8(g * a / 255)
        op[di * 4 + 2] = UInt8(b * a / 255); op[di * 4 + 3] = UInt8(a)
    }
}
let rep = NSBitmapImageRep(cgImage: out.makeImage()!)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[2]))
print("ok \(cw)x\(ch)")
// Background colours for the tile (top-left corner, centre-top, centre of the icon).
for (x, y) in [(2, 2), (w / 2, h / 6), (w / 2, h / 2 - h / 4), (w - 3, h - 3), (2, h / 2)] {
    let (r, g, b) = rgb(y * w + x); print("bg sample \(x / scale),\(y / scale): \(r),\(g),\(b)")
}
