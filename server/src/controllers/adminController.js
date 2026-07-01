const fs = require('fs');
const path = require('path');
const mm = require('music-metadata');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Album = require('../models/Album');
const { parseAudioMetadata } = require('../utils/id3Parser');
const { processCover, processCoverBuffer } = require('../utils/imageProcessor');
const SongFile = require('../models/SongFile');
const { transcodeForNokia } = require('../utils/audioTranscoder');
const { resolveSongCover, getCoverIcon } = require('../utils/coverHelper');
const { MUSIC_DIR, COVERS_DIR, TMP_COVERS_DIR, deleteSongFiles, getMimeTypeFromFilename, generateUniqueFilename } = require('../utils/fileHelper');

const OUTPUT_EXT = '.mp3';
const OUTPUT_MIME = 'audio/mpeg';

function getDashboard(req, res) {
    const totalSongs = Song.count();
    const totalArtists = Artist.count();
    const totalAlbums = Album.count();

    res.render('dashboard', {
        user: req.session.username,
        stats: {
            songs: totalSongs,
            artists: totalArtists,
            albums: totalAlbums
        }
    });
}

function getSongs(req, res) {
    const page = parseInt(req.query.page) || 0;
    const size = 20;
    const songs = Song.getAll({ page, size });
    const total = Song.count();
    const totalPages = Math.ceil(total / size);

    // Precargar calidades disponibles por canción
    const songsWithQualities = songs.map(song => {
        const files = SongFile.getAllBySong(song.id);
        song.qualities = files.map(f => f.quality);
        return song;
    });

    res.render('songs', {
        user: req.session.username,
        songs: songsWithQualities,
        page,
        totalPages,
        total,
        resolveSongCover,
        getCoverIcon
    });
}

function newSongForm(req, res) {
    const artists = Artist.getAll({ size: 1000 });
    const albums = Album.getAll({ size: 1000 });

    res.render('song-form', {
        user: req.session.username,
        song: null,
        artists,
        albums,
        error: null
    });
}

/**
 * Genera las versiones de audio por calidad que quepan en el límite RMS del cliente.
 * Devuelve un objeto { success: true, files: [{quality, filename, fileSize}] }
 * o { success: false, error: string, tooLong?: boolean }.
 */
async function generateQualityFiles(inputPath, songId, duration) {
    const fittingQualities = SongFile.getFittingQualities(duration);

    if (fittingQualities.length === 0) {
        const maxSeconds = Math.floor(SongFile.getMaxRmsSize() * 8 / (32 * 1000));
        const maxMinutes = Math.floor(maxSeconds / 60);
        const maxRemaining = maxSeconds % 60;
        return {
            success: false,
            tooLong: true,
            error: `La canción dura ${duration}s y no cabe en la memoria del móvil (límite 1 MB). Máximo permitido a 32 kbps: ${maxMinutes}m ${maxRemaining}s.`
        };
    }

    const files = [];
    for (const quality of fittingQualities) {
        const filename = `song-${Date.now()}-${Math.floor(Math.random() * 10000)}-${quality}k${OUTPUT_EXT}`;
        const outputPath = path.join(MUSIC_DIR, filename);
        const result = await transcodeForNokia(inputPath, outputPath, `${quality}k`);

        if (!result.success) {
            // Limpiar archivos ya generados en caso de fallo
            for (const f of files) {
                const p = path.join(MUSIC_DIR, f.filename);
                try { if (fs.existsSync(p)) fs.unlinkSync(p); } catch (e) {}
            }
            return { success: false, error: `Error transcodificando ${quality}k: ${result.error}` };
        }

        SongFile.create(songId, quality, filename, result.fileSize || 0);
        files.push({ quality, filename, fileSize: result.fileSize || 0 });
    }

    return { success: true, files };
}

async function createSong(req, res) {
    try {
        const musicFile = req.files && req.files.audio ? req.files.audio[0] : null;
        const coverFile = req.files && req.files.cover ? req.files.cover[0] : null;

        if (!musicFile) {
            return res.render('song-form', {
                user: req.session.username,
                song: null,
                artists: Artist.getAll({ size: 1000 }),
                albums: Album.getAll({ size: 1000 }),
                error: 'Debes subir al menos el archivo de audio.'
            });
        }

        const metadata = await parseAudioMetadata(musicFile.path);

        const title = req.body.title && req.body.title.trim() ? req.body.title.trim() : (metadata.title || 'Sin título');
        const artistName = req.body.artist && req.body.artist.trim() ? req.body.artist.trim() : (metadata.artist || '');
        const albumName = req.body.album && req.body.album.trim() ? req.body.album.trim() : (metadata.album || '');
        const duration = req.body.duration ? parseInt(req.body.duration) : (metadata.duration || 0);

        let artist = null;
        let album = null;

        if (artistName) {
            artist = Artist.findOrCreate(artistName);
        }

        if (albumName) {
            album = Album.findOrCreate(albumName, artist ? artist.id : null);
        }

        // Creamos la canción primero para obtener el ID
        const song = Song.create({
            title,
            artistId: artist ? artist.id : null,
            albumId: album ? album.id : null,
            duration,
            cover: null,
            filename: '', // legacy, se mantiene vacío o se rellena con la mejor calidad
            mimeType: OUTPUT_MIME
        });

        // Generar versiones por calidad
        const qualityResult = await generateQualityFiles(musicFile.path, song.id, duration);
        if (!qualityResult.success) {
            // Rollback: borrar canción creada
            Song.delete(song.id);
            throw new Error(qualityResult.error);
        }

        // Rellenar filename legacy con la mejor calidad generada
        const bestFile = qualityResult.files[qualityResult.files.length - 1];
        Song.update(song.id, { filename: bestFile.filename });

        // Eliminar archivo original subido por multer
        try { fs.unlinkSync(musicFile.path); } catch (e) {}

        let coverFilename = null;

        if (coverFile) {
            coverFilename = await processCover(coverFile.path);
            // Borrar archivo original subido por multer
            try { fs.unlinkSync(coverFile.path); } catch (e) {}
        } else if (metadata.coverFilename) {
            const originalCoverPath = path.join(COVERS_DIR, metadata.coverFilename);
            if (fs.existsSync(originalCoverPath)) {
                coverFilename = await processCover(originalCoverPath);
            }
        }

        if (coverFilename) {
            Song.update(song.id, { cover: coverFilename });
        }

        res.redirect('/admin/songs');
    } catch (err) {
        console.error('Error creando canción:', err);
        res.render('song-form', {
            user: req.session.username,
            song: null,
            artists: Artist.getAll({ size: 1000 }),
            albums: Album.getAll({ size: 1000 }),
            error: 'Error al procesar la canción: ' + err.message
        });
    }
}

async function editSongForm(req, res) {
    const id = parseInt(req.params.id);
    const song = Song.getById(id);

    if (!song) {
        return res.redirect('/admin/songs');
    }

    const artists = Artist.getAll({ size: 1000 });
    const albums = Album.getAll({ size: 1000 });

    res.render('song-edit', {
        user: req.session.username,
        song,
        artists,
        albums,
        error: null,
        resolveSongCover,
        getCoverIcon
    });
}

async function updateSong(req, res) {
    const id = parseInt(req.params.id);
    const song = Song.getById(id);

    if (!song) {
        return res.redirect('/admin/songs');
    }

    try {
        const musicFile = req.files && req.files.audio ? req.files.audio[0] : null;
        const coverFile = req.files && req.files.cover ? req.files.cover[0] : null;

        const formArtist = req.body.artist !== undefined ? req.body.artist.trim() : '';
        const formAlbum = req.body.album !== undefined ? req.body.album.trim() : '';

        let updates = {
            title: req.body.title && req.body.title.trim() ? req.body.title.trim() : song.title,
            artistId: song.artist_id,
            albumId: song.album_id,
            duration: req.body.duration ? parseInt(req.body.duration) : song.duration,
            filename: song.filename,
            cover: song.cover,
            mimeType: song.mime_type
        };

        // Resolver artista
        if (formArtist) {
            const artist = Artist.findOrCreate(formArtist);
            updates.artistId = artist ? artist.id : null;
        } else {
            updates.artistId = null;
        }

        // Resolver álbum
        if (formAlbum) {
            const album = Album.findOrCreate(formAlbum, updates.artistId);
            updates.albumId = album ? album.id : null;
        } else {
            updates.albumId = null;
        }

        // Si se sube nuevo audio
        if (musicFile) {
            const metadata = await parseAudioMetadata(musicFile.path);

            if (!updates.title || updates.title === 'Sin título') updates.title = metadata.title || 'Sin título';

            const newDuration = !req.body.duration
                ? (metadata.duration || song.duration)
                : updates.duration;

            // Generar nuevas calidades
            const qualityResult = await generateQualityFiles(musicFile.path, id, newDuration);
            if (!qualityResult.success) {
                throw new Error(qualityResult.error);
            }

            // Borrar archivos de calidad antiguos
            const oldFiles = SongFile.getAllBySong(id);
            for (const f of oldFiles) {
                const oldPath = path.join(MUSIC_DIR, f.filename);
                try { if (fs.existsSync(oldPath)) fs.unlinkSync(oldPath); } catch (e) {}
            }
            SongFile.deleteBySong(id);

            // Guardar nuevos archivos en BD
            for (const f of qualityResult.files) {
                SongFile.create(id, f.quality, f.filename, f.fileSize);
            }

            // Actualizar filename legacy con la mejor calidad
            const bestFile = qualityResult.files[qualityResult.files.length - 1];
            updates.filename = bestFile.filename;
            updates.mimeType = OUTPUT_MIME;
            updates.duration = newDuration;

            // Eliminar archivo original subido por multer
            try { fs.unlinkSync(musicFile.path); } catch (e) {}

            if (!coverFile && metadata.coverFilename && !updates.cover) {
                const originalCoverPath = path.join(COVERS_DIR, metadata.coverFilename);
                if (fs.existsSync(originalCoverPath)) {
                    updates.cover = await processCover(originalCoverPath);
                }
            }
        }

        // Si se sube nueva carátula
        if (coverFile) {
            if (song.cover) {
                const oldCoverPath = path.join(COVERS_DIR, song.cover);
                if (fs.existsSync(oldCoverPath)) fs.unlinkSync(oldCoverPath);
            }
            updates.cover = await processCover(coverFile.path);
            try { fs.unlinkSync(coverFile.path); } catch (e) {}
        }

        Song.update(id, updates);
        res.redirect('/admin/songs');
    } catch (err) {
        console.error('Error actualizando canción:', err);
        res.render('song-edit', {
            user: req.session.username,
            song,
            artists: Artist.getAll({ size: 1000 }),
            albums: Album.getAll({ size: 1000 }),
            error: 'Error al actualizar la canción: ' + err.message
        });
    }
}

function deleteSong(req, res) {
    const id = parseInt(req.params.id);
    const song = Song.getById(id);

    if (song) {
        const qualityFiles = SongFile.getAllBySong(id).map(f => f.filename);
        deleteSongFiles(song, qualityFiles);
        SongFile.deleteBySong(id);
        Song.delete(id);
    }

    res.redirect('/admin/songs');
}

async function previewMetadata(req, res) {
    try {
        const musicFile = req.file;
        if (!musicFile) {
            return res.status(400).json({ error: 'No se ha enviado ningún archivo de audio.' });
        }

        let metadata;
        try {
            metadata = await mm.parseFile(musicFile.path);
        } catch (parseErr) {
            metadata = { common: {}, format: {} };
        }

        const common = metadata.common || {};
        const format = metadata.format || {};

        // Extraer carátula incrustada si existe, procesarla y guardarla temporalmente
        let coverUrl = null;
        if (common.picture && common.picture.length > 0) {
            const picture = common.picture[0];
            const processedName = await processCoverBuffer(picture.data, TMP_COVERS_DIR);
            if (processedName) {
                coverUrl = `/tmp-covers/${processedName}`;
            }
        }

        try {
            fs.unlinkSync(musicFile.path);
        } catch (err) {
            console.error('Error borrando archivo temporal:', err.message);
        }

        res.json({
            title: common.title || '',
            artist: common.artist || '',
            album: common.album || '',
            duration: format.duration ? Math.round(format.duration) : 0,
            mimeType: format.mimeType || getMimeTypeFromFilename(musicFile.originalname),
            coverUrl
        });
    } catch (err) {
        console.error('Error en previewMetadata:', err);
        res.status(500).json({ error: 'Error al leer los metadatos: ' + err.message });
    }
}

async function ensureArtistAlbum(req, res) {
    try {
        const { artist, album } = req.body;

        let artistObj = null;
        let albumObj = null;

        if (artist && artist.trim()) {
            artistObj = Artist.findOrCreate(artist.trim());
        }

        if (album && album.trim()) {
            albumObj = Album.findOrCreate(album.trim(), artistObj ? artistObj.id : null);
        }

        res.json({
            artistId: artistObj ? artistObj.id : null,
            artistName: artistObj ? artistObj.name : '',
            albumId: albumObj ? albumObj.id : null,
            albumName: albumObj ? albumObj.title : ''
        });
    } catch (err) {
        console.error('Error en ensureArtistAlbum:', err);
        res.status(500).json({ error: err.message });
    }
}

module.exports = {
    getDashboard,
    getSongs,
    newSongForm,
    createSong,
    editSongForm,
    updateSong,
    deleteSong,
    previewMetadata,
    ensureArtistAlbum
};
