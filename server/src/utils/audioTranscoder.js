const { spawn } = require('child_process');
const fs = require('fs');

/**
 * Formato de salida: 'mp3' o 'amr'.
 * amr = AMR-NB 12.2k, optimo para redes 2G y Nokia 6111.
 * mp3 = MP3 CBR segun AUDIO_BITRATE.
 */
const AUDIO_FORMAT = (process.env.AUDIO_FORMAT || 'mp3').toLowerCase();

/**
 * Bitrate de salida para MP3. Valores validos: 32k, 48k, 64k, 96k, 128k.
 */
const AUDIO_BITRATE = (process.env.AUDIO_BITRATE || '64k').toLowerCase();

/**
 * Transcodifica cualquier archivo de audio a un formato compatible con Nokia 6111.
 * Formato configurable via AUDIO_FORMAT (.env):
 * - amr: AMR-NB 12.2k, 8000 Hz, mono (recomendado para 2G)
 * - mp3: MP3 CBR, 22050 Hz, mono
 *
 * @param {string} inputPath ruta del archivo original
 * @param {string} outputPath ruta destino
 * @returns {Promise<{duration: number, success: boolean, error?: string}>}
 */
function transcodeForNokia(inputPath, outputPath) {
    return new Promise((resolve) => {
        if (!fs.existsSync(inputPath)) {
            resolve({ success: false, error: 'Archivo de entrada no encontrado' });
            return;
        }

        let args;
        if (AUDIO_FORMAT === 'amr') {
            args = [
                '-y',
                '-i', inputPath,
                '-vn',
                '-codec:a', 'libopencore_amrnb',
                '-ac', '1',
                '-ar', '8000',
                '-ab', '12.2k',
                outputPath
            ];
        } else {
            args = [
                '-y',
                '-i', inputPath,
                '-vn',
                '-map_metadata', '-1',
                '-codec:a', 'libmp3lame',
                '-b:a', AUDIO_BITRATE,
                '-ar', '22050',
                '-ac', '1',
                '-id3v2_version', '0',
                '-write_xing', '0',
                outputPath
            ];
        }

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

            // Obtener duración del archivo transcodificado
            getDuration(outputPath).then((duration) => {
                resolve({ success: true, duration });
            }).catch((err) => {
                resolve({ success: true, duration: 0, warning: err.message });
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
