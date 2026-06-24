const Jimp = require('jimp');
const path = require('path');
const fs = require('fs');
const { generateUniqueFilename, COVERS_DIR } = require('./fileHelper');

const COVER_SIZE = 300;
const COVER_QUALITY = 90;

async function processCover(inputPath, outputDir = COVERS_DIR) {
    try {
        const image = await Jimp.read(inputPath);

        const width = image.getWidth();
        const height = image.getHeight();
        const size = Math.min(width, height);
        const x = (width - size) / 2;
        const y = (height - size) / 2;

        image.crop(x, y, size, size);
        image.resize(COVER_SIZE, COVER_SIZE, Jimp.RESIZE_BICUBIC);
        image.quality(COVER_QUALITY);

        const outputName = generateUniqueFilename('cover', 'cover.jpg');
        const outputPath = path.join(outputDir, outputName);

        await image.writeAsync(outputPath);

        return outputName;
    } catch (err) {
        console.error('Error procesando carátula:', err.message);
        return null;
    }
}

async function processCoverBuffer(buffer, outputDir = COVERS_DIR) {
    try {
        const image = await Jimp.read(buffer);

        const width = image.getWidth();
        const height = image.getHeight();
        const size = Math.min(width, height);
        const x = (width - size) / 2;
        const y = (height - size) / 2;

        image.crop(x, y, size, size);
        image.resize(COVER_SIZE, COVER_SIZE, Jimp.RESIZE_BICUBIC);
        image.quality(COVER_QUALITY);

        const outputName = generateUniqueFilename('cover', 'cover.jpg');
        const outputPath = path.join(outputDir, outputName);

        await image.writeAsync(outputPath);

        return outputName;
    } catch (err) {
        console.error('Error procesando buffer de carátula:', err.message);
        return null;
    }
}

module.exports = {
    processCover,
    processCoverBuffer,
    COVER_SIZE
};
