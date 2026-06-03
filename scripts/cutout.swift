import Foundation
import Vision
import CoreImage
import AppKit

func removeBackground(input: String, output: String) {
    guard let img = CIImage(contentsOf: URL(fileURLWithPath: input)) else {
        FileHandle.standardError.write("load failed: \(input)\n".data(using: .utf8)!)
        exit(1)
    }
    let handler = VNImageRequestHandler(ciImage: img, options: [:])
    let request = VNGenerateForegroundInstanceMaskRequest()
    do {
        try handler.perform([request])
    } catch {
        FileHandle.standardError.write("vision failed: \(error)\n".data(using: .utf8)!)
        exit(1)
    }
    guard let result = request.results?.first else {
        FileHandle.standardError.write("no subject found: \(input)\n".data(using: .utf8)!)
        exit(1)
    }
    do {
        let buffer = try result.generateMaskedImage(
            ofInstances: result.allInstances,
            from: handler,
            croppedToInstancesExtent: true)
        let ciOut = CIImage(cvPixelBuffer: buffer)
        let ctx = CIContext()
        guard let cg = ctx.createCGImage(ciOut, from: ciOut.extent) else {
            FileHandle.standardError.write("cgimage failed\n".data(using: .utf8)!)
            exit(1)
        }
        let rep = NSBitmapImageRep(cgImage: cg)
        guard let data = rep.representation(using: .png, properties: [:]) else {
            FileHandle.standardError.write("png encode failed\n".data(using: .utf8)!)
            exit(1)
        }
        try data.write(to: URL(fileURLWithPath: output))
        print("cutout -> \(output)")
    } catch {
        FileHandle.standardError.write("mask failed: \(error)\n".data(using: .utf8)!)
        exit(1)
    }
}

let args = CommandLine.arguments
guard args.count == 3 else {
    print("usage: cutout <input> <output>")
    exit(2)
}
removeBackground(input: args[1], output: args[2])
