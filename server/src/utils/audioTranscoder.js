const { spawn } = require('child_process');
const fs = require('fs');

/**
 * Transcodifica cualquier archivo de audio a MP3 compatible con Nokia 6111.
 * Salida: MP3 CBR, mono, 22050 Hz.
 *
 * @param {string} inputPath ruta del archivo original
 * @param {string} outputPath ruta destino
 * @param {string} bitrate bitrate CBR válido para libmp3lame (ej. '32k', '64k', '128k')
 * @returns {Promise<{duration: number, success: boolean, error?: string, fileSize?: number}>}
 */
function transcodeForNokia(inputPath, outputPath, bitrate) {
    return new Promise((resolve) => {
        if (!fs.existsSync(inputPath)) {
            resolve({ success: false, error: 'Archivo de entrada no encontrado' });
            return;
        }

        const safeBitrate = bitrate || '64k';

        const args = [
            '-y',
            '-i', inputPath,
            '-vn',
            '-map_metadata', '-1',
            '-codec:a', 'libmp3lame',
            '-b:a', safeBitrate,
            '-ar', '22050',
            '-ac', '1',
            '-id3v2_version', '0',
            '-write_xing', '0',
            outputPath
        ];

        const proc = spawn('ffmpeg', args, { stdio: ['ignore', 'pipe', 'pipe'] });
        let stderr = '';

        proc.stderr.on('data', (data) => {
            stderr += data.toString();
        });

        proc.on('close', (code) => {
            if (code !== 0 || !fs.existsSync(outputPath)) {
                resolve({ success: false, error: `ffmpeg falló con código ${code}: ${stderr.slice(0, 200)}` });
                return;
            }

            const fileSize = fs.statSync(outputPath).size;

            // Obtener duración del archivo transcodificado
            getDuration(outputPath).then((duration) => {
                resolve({ success: true, duration, fileSize });
            }).catch((err) => {
                resolve({ success: true, duration: 0, fileSize, warning: err.message });
            });
        });

        proc.on('error', (err) => {
            resolve({ success: false, error: err.message });
        });
    });
}

/**
 * Obtiene la duración en segundos de un archivo de audio usando ffprobe.
 */
function getDuration(audioPath) {
    return new Promise((resolve, reject) => {
        const proc = spawn('ffprobe', [
            '-v', 'error',
            '-show_entries', 'format=duration',
            '-of', 'default=noprint_wrappers=1:nokey=1',
            audioPath
        ], { stdio: ['ignore', 'pipe', 'pipe'] });

        let stdout = '';
        proc.stdout.on('data', (data) => {
            stdout += data.toString();
        });

        proc.on('close', (code) => {
            if (code !== 0) {
                reject(new Error('ffprobe falló'));
                return;
            }
            const seconds = parseFloat(stdout.trim());
            resolve(isNaN(seconds) ? 0 : Math.round(seconds));
        });

        proc.on('error', (err) => reject(err));
    });
}

module.exports = { transcodeForNokia, getDuration };
