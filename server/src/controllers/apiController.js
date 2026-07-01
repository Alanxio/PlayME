const fs = require('fs');
const path = require('path');
const Jimp = require('jimp');
const Song = require('../models/Song');
const Album = require('../models/Album');
const SongFile = require('../models/SongFile');
const { MUSIC_DIR, COVERS_DIR, getMimeTypeFromFilename } = require('../utils/fileHelper');

function coverFileExists(filename) {
    if (!filename) return false;
    return fs.existsSync(path.join(COVERS_DIR, filename));
}

function sendJSON(res, data) {
    const json = JSON.stringify(data);
    res.writeHead(200, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(json),
        'Access-Control-Allow-Origin': '*'
    });
    res.end(json);
}

function resolveCover(song) {
    // 1. Cover propio de la canción
    if (song.cover && coverFileExists(song.cover)) return song.cover;

    // 2. Cover del álbum
    if (song.album_id) {
        const album = Album.getById(song.album_id);
        if (album && album.cover && coverFileExists(album.cover)) return album.cover;

        // 3. Fallback: cover de alguna canción del mismo álbum
        if (album && album.title) {
            const albumSongs = Song.getAll({ album: album.title, size: 1000 });
            for (const s of albumSongs) {
                if (s.cover && coverFileExists(s.cover)) return s.cover;
            }
        }
    }

    return process.env.DEFAULT_COVER || 'default.jpg';
}

function formatSong(song) {
    if (!song) return null;
    return {
        id: song.id,
        title: song.title,
        artist: song.artist || '',
        album: song.album || '',
        duration: song.duration,
        cover: resolveCover(song),
        filename: song.filename,
        mime_type: song.mime_type,
        created_at: song.created_at
    };
}

function getCatalog(req, res) {
    const page = parseInt(req.query.page) || 0;
    const size = parseInt(req.query.size) || 20;
    const artist = req.query.artist || null;
    const album = req.query.album || null;
    const search = req.query.search || null;

    const songs = Song.getAll({ page, size, artist, album, search });
    const total = Song.count({ artist, album, search });

    sendJSON(res, {
        total,
        page,
        songs: songs.map(formatSong)
    });
}

function getCatalogCount(req, res) {
    sendJSON(res, { total: Song.count() });
}

function getArtists(req, res) {
    sendJSON(res, Song.getArtists());
}

function getAlbums(req, res) {
    sendJSON(res, Song.getAlbums());
}

function getSong(req, res) {
    const id = parseInt(req.params.id);
    const song = Song.getById(id);

    if (!song) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Song not found' }));
    }

    sendJSON(res, formatSong(song));
}

async function getCover(req, res) {
    const filename = decodeURIComponent(req.params.filename);
    const filepath = path.join(COVERS_DIR, path.basename(filename));
    const requestedSize = parseInt(req.query.size, 10) || 0;
    const thumbSize = requestedSize > 0 && requestedSize <= 256 ? requestedSize : 0;

    const coverPath = fs.existsSync(filepath) ? filepath : null;
    const defaultCover = process.env.DEFAULT_COVER || 'default.jpg';
    const defaultPath = path.join(COVERS_DIR, defaultCover);
    const finalPath = coverPath || (fs.existsSync(defaultPath) ? defaultPath : null);

    if (!finalPath) {
        res.writeHead(404);
        return res.end('Cover not found');
    }

    try {
        let data;
        const ext = path.extname(finalPath).toLowerCase();
        const contentType = ext === '.png' ? 'image/png' : 'image/jpeg';

        if (thumbSize > 0) {
            const image = await Jimp.read(finalPath);
            image.cover(thumbSize, thumbSize);
            data = await image.getBufferAsync(contentType === 'image/png' ? Jimp.MIME_PNG : Jimp.MIME_JPEG);
        } else {
            data = fs.readFileSync(finalPath);
        }

        res.writeHead(200, {
            'Content-Type': contentType,
            'Content-Length': data.length
        });
        return res.end(data);
    } catch (err) {
        console.error('[getCover] error', err);
        res.writeHead(500);
        res.end('Error processing cover');
    }
}

function streamSong(req, res) {
    const id = parseInt(req.params.id);
    const song = Song.getById(id);

    if (!song) {
        console.log(`[stream] id=${id} NOT_FOUND song`);
        res.writeHead(404);
        return res.end('Song not found');
    }

    // Seleccionar archivo por calidad solicitada, con fallback
    const requestedQuality = parseInt(req.query.quality, 10) || 64;
    let songFile = SongFile.getBestForSong(id, requestedQuality);

    // Fallback legacy: si no hay entradas en song_files, usar song.filename
    let filename = song.filename;
    if (songFile) {
        filename = songFile.filename;
    }

    const filepath = path.join(MUSIC_DIR, filename);
    const contentType = song.mime_type || getMimeTypeFromFilename(filename);

    if (!fs.existsSync(filepath)) {
        console.log(`[stream] id=${id} NOT_FOUND file=${filepath}`);
        res.writeHead(404);
        return res.end('File not found');
    }

    const stat = fs.statSync(filepath);
    const fileSize = stat.size;
    const rangeHeader = req.headers.range;
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;

    console.log(`[stream] id=${id} quality=${requestedQuality} file=${filename} mime=${contentType} size=${fileSize} range=${rangeHeader || 'none'} client=${clientIp}`);

    // HEAD: solo informacion del archivo
    if (req.method === 'HEAD') {
        res.writeHead(200, {
            'Content-Type': contentType,
            'Content-Length': fileSize,
            'Accept-Ranges': 'bytes'
        });
        return res.end();
    }

    // Range request: reanudar descarga parcial
    if (rangeHeader) {
        const parts = rangeHeader.replace(/bytes=/, '').split('-');
        const start = parseInt(parts[0], 10);
        const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

        if (!isNaN(start) && !isNaN(end) && start >= 0 && end < fileSize && start <= end) {
            const chunkSize = end - start + 1;
            console.log(`[stream] id=${id} RANGE start=${start} end=${end} chunk=${chunkSize}`);
            res.writeHead(206, {
                'Content-Type': contentType,
                'Content-Length': chunkSize,
                'Content-Range': `bytes ${start}-${end}/${fileSize}`,
                'Accept-Ranges': 'bytes'
            });
            const stream = fs.createReadStream(filepath, { start, end });
            stream.on('end', () => console.log(`[stream] id=${id} RANGE completed`));
            stream.on('error', (e) => console.log(`[stream] id=${id} RANGE error=${e.message}`));
            return stream.pipe(res);
        }

        // Rango invalido
        console.log(`[stream] id=${id} RANGE_INVALID`);
        res.writeHead(416, {
            'Content-Range': `bytes */${fileSize}`,
            'Accept-Ranges': 'bytes'
        });
        return res.end();
    }

    // Respuesta completa sin range: enviar a maxima velocidad.
    // La app J2ME ahora descarga el audio completo a memoria antes de
    // reproducir, por lo que el throttling no es necesario y de hecho
    // provoca timeouts en la pila HTTP del Nokia Series 40.
    console.log(`[stream] id=${id} FULL_DOWNLOAD no-throttle`);
    res.writeHead(200, {
        'Content-Type': contentType,
        'Content-Length': fileSize,
        'Accept-Ranges': 'bytes'
    });
    const stream = fs.createReadStream(filepath);
    stream.on('end', () => console.log(`[stream] id=${id} FULL_DOWNLOAD completed`));
    stream.on('error', (e) => console.log(`[stream] id=${id} FULL_DOWNLOAD error=${e.message}`));
    stream.pipe(res);
}

/**
 * Envía un archivo con rafaga inicial + throttling sostenido.
 * Esto evita que el Nokia 6111 (Series 40) desborde su buffer de red
 * y cierre el socket a los pocos segundos de reproduccion.
 */
function streamWithThrottling(id, filepath, fileSize, contentType, res) {
    const isAmr = contentType === 'audio/amr';
    const INITIAL_BURST = 32 * 1024;        // 32 KB iniciales
    const AMR_CHUNK = 2 * 1024;             // 2 KB/s para AMR-NB ~12.2kbps
    const MP3_CHUNK = 10 * 1024;            // 10 KB/s para MP3 ~64kbps
    const CHUNK_INTERVAL = 1000;            // 1 segundo

    const chunkSize = isAmr ? AMR_CHUNK : MP3_CHUNK;
    let position = 0;
    let closed = false;
    let timer = null;
    let completed = false;
    const startTime = Date.now();

    function cleanup() {
        if (closed) return;
        closed = true;
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
        const elapsed = Date.now() - startTime;
        if (!completed) {
            console.log(`[stream] id=${id} CLIENT_DISCONNECT bytesSent=${position} elapsedMs=${elapsed}`);
        }
    }

    res.on('close', cleanup);
    res.on('error', (e) => {
        console.log(`[stream] id=${id} RES_ERROR error=${e.message}`);
        cleanup();
    });

    fs.open(filepath, 'r', (err, fd) => {
        if (err || closed) {
            console.log(`[stream] id=${id} OPEN_ERROR error=${err ? err.message : 'closed'}`);
            if (!closed) res.end();
            return;
        }

        console.log(`[stream] id=${id} throttle start burst=${Math.min(INITIAL_BURST, fileSize)} chunk=${chunkSize}B/${CHUNK_INTERVAL}ms`);

        // 1. Rafaga inicial
        const initialSize = Math.min(INITIAL_BURST, fileSize);
        const initialBuffer = Buffer.alloc(initialSize);
        fs.read(fd, initialBuffer, 0, initialSize, 0, (err2) => {
            if (err2 || closed) {
                console.log(`[stream] id=${id} READ_ERROR phase=burst error=${err2 ? err2.message : 'closed'}`);
                fs.close(fd, () => {});
                if (!closed) res.end();
                return;
            }

            res.write(initialBuffer);
            position = initialSize;
            console.log(`[stream] id=${id} burst_sent bytes=${initialSize} position=${position}`);

            if (position >= fileSize) {
                completed = true;
                const elapsed = Date.now() - startTime;
                console.log(`[stream] id=${id} COMPLETED small_file bytes=${position} elapsedMs=${elapsed}`);
                fs.close(fd, () => {});
                res.end();
                return;
            }

            // 2. Throttling sostenido: enviar chunks cada segundo
            timer = setInterval(() => {
                if (closed) {
                    fs.close(fd, () => {});
                    return;
                }

                const remaining = fileSize - position;
                if (remaining <= 0) {
                    completed = true;
                    clearInterval(timer);
                    timer = null;
                    const elapsed = Date.now() - startTime;
                    console.log(`[stream] id=${id} COMPLETED bytes=${position} elapsedMs=${elapsed}`);
                    fs.close(fd, () => {});
                    res.end();
                    return;
                }

                const size = Math.min(chunkSize, remaining);
                const buffer = Buffer.alloc(size);
                fs.read(fd, buffer, 0, size, position, (err3) => {
                    if (err3 || closed) {
                        if (timer) clearInterval(timer);
                        fs.close(fd, () => {});
                        if (!closed) res.end();
                        return;
                    }
                    const ok = res.write(buffer);
                    position += size;
                    if (!ok) {
                        console.log(`[stream] id=${id} BACKPRESSURE position=${position}`);
                    }
                });
            }, CHUNK_INTERVAL);
        });
    });
}

module.exports = {
    getCatalog,
    getCatalogCount,
    getArtists,
    getAlbums,
    getSong,
    getCover,
    streamSong
};
