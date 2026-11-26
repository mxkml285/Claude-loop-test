// Renders the 320x180 Google TV banner (res/drawable-xhdpi/banner.png). Run: swift tools/make-banner.swift <out.png>
import AppKit
let w = 320, h = 180
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h, bitsPerSample: 8,
    samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
let g = NSGradient(starting: NSColor(red: 0.98, green: 0.62, blue: 0.25, alpha: 1),
                   ending: NSColor(red: 0.11, green: 0.14, blue: 0.30, alpha: 1))!
g.draw(in: NSRect(x: 0, y: 0, width: w, height: h), angle: -35)
NSColor(white: 1, alpha: 0.9).setFill()
NSBezierPath(ovalIn: NSRect(x: 26, y: 66, width: 48, height: 48)).fill()
NSColor(red: 0.11, green: 0.14, blue: 0.30, alpha: 1).setFill()
NSBezierPath(ovalIn: NSRect(x: 44, y: 78, width: 40, height: 40)).fill()
let style = NSMutableParagraphStyle(); style.alignment = .left
let a1: [NSAttributedString.Key: Any] = [.font: NSFont.boldSystemFont(ofSize: 34), .foregroundColor: NSColor.white, .paragraphStyle: style]
let a2: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 22), .foregroundColor: NSColor(white: 1, alpha: 0.85), .paragraphStyle: style]
("Smart" as NSString).draw(at: NSPoint(x: 100, y: 92), withAttributes: a1)
("Comfort" as NSString).draw(at: NSPoint(x: 100, y: 62), withAttributes: a2)
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
