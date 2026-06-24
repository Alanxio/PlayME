const fs = require('fs');
const path = require('path');
const Album = require('../models/Album');
const Artist = require('../models/Artist');
const { processCover } = require('../utils/imageProcessor');
const { COVERS_DIR, safeDelete } = require('../utils/fileHelper');

function getAlbums(req, res) {
    const page = parseInt(req.query.page) || 0;
    const search = req.query.search || null;
    const artistId = req.query.artist ? parseInt(req.query.artist) : null;
    const size = 50;

    const albums = Album.getAll({ page, size, search, artistId });
    const total = Album.count();
    const totalPages = Math.ceil(total / size);
    const artists = Artist.getAll({ size: 1000 });

    res.render('albums', {
        user: req.session.username,
        albums,
        artists,
        page,
        totalPages,
        total,
        search,
        artistId
    });
}

function newAlbumForm(req, res) {
    const artists = Artist.getAll({ size: 1000 });
    res.render('album-form', {
        user: req.session.username,
        album: null,
        artists,
        error: null
    });
}

async function createAlbum(req, res) {
    try {
        const title = req.body.title ? req.body.title.trim() : '';
        const artistId = req.body.artist_id ? parseInt(req.body.artist_id) : null;

        if (!title) {
            const artists = Artist.getAll({ size: 1000 });
            return res.render('album-form', {
                user: req.session.username,
                album: null,
                artists,
                error: 'El título del álbum es obligatorio.'
            });
        }

        const coverFile = req.file;
        let coverFilename = null;

        if (coverFile) {
            coverFilename = await processCover(coverFile.path);
            try { fs.unlinkSync(coverFile.path); } catch (e) {}
        }

        Album.create(title, artistId);
        const newAlbum = Album.findByTitleAndArtist(title, artistId);
        if (newAlbum && coverFilename) {
            Album.update(newAlbum.id, title, artistId, coverFilename);
        }

        res.redirect('/admin/albums');
    } catch (err) {
        console.error('Error creando álbum:', err);
        const artists = Artist.getAll({ size: 1000 });
        res.render('album-form', {
            user: req.session.username,
            album: null,
            artists,
            error: 'Error al crear el álbum: ' + err.message
        });
    }
}

function editAlbumForm(req, res) {
    const id = parseInt(req.params.id);
    const album = Album.getById(id);

    if (!album) {
        return res.redirect('/admin/albums');
    }

    const artists = Artist.getAll({ size: 1000 });
    res.render('album-form', {
        user: req.session.username,
        album,
        artists,
        error: null
    });
}

async function updateAlbum(req, res) {
    const id = parseInt(req.params.id);
    const album = Album.getById(id);

    if (!album) {
        return res.redirect('/admin/albums');
    }

    const title = req.body.title ? req.body.title.trim() : '';
    const artistId = req.body.artist_id ? parseInt(req.body.artist_id) : null;

    if (!title) {
        const artists = Artist.getAll({ size: 1000 });
        return res.render('album-form', {
            user: req.session.username,
            album,
            artists,
            error: 'El título del álbum es obligatorio.'
        });
    }

    let cover = album.cover;
    if (req.file) {
        if (album.cover) {
            safeDelete(album.cover, COVERS_DIR);
        }
        cover = await processCover(req.file.path);
        try { fs.unlinkSync(req.file.path); } catch (e) {}
    }

    Album.update(id, title, artistId, cover);
    res.redirect('/admin/albums');
}

function deleteAlbum(req, res) {
    const id = parseInt(req.params.id);
    const album = Album.getById(id);

    if (album && album.cover) {
        safeDelete(album.cover, COVERS_DIR);
    }

    Album.delete(id);
    res.redirect('/admin/albums');
}

module.exports = {
    getAlbums,
    newAlbumForm,
    createAlbum,
    editAlbumForm,
    updateAlbum,
    deleteAlbum
};
