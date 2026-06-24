const Artist = require('../models/Artist');

function getArtists(req, res) {
    const page = parseInt(req.query.page) || 0;
    const search = req.query.search || null;
    const size = 50;

    const artists = Artist.getAll({ page, size, search });
    const total = Artist.count();
    const totalPages = Math.ceil(total / size);

    res.render('artists', {
        user: req.session.username,
        artists,
        page,
        totalPages,
        total,
        search
    });
}

function newArtistForm(req, res) {
    res.render('artist-form', {
        user: req.session.username,
        artist: null,
        error: null
    });
}

function createArtist(req, res) {
    const name = req.body.name ? req.body.name.trim() : '';

    if (!name) {
        return res.render('artist-form', {
            user: req.session.username,
            artist: null,
            error: 'El nombre del artista es obligatorio.'
        });
    }

    Artist.create(name);
    res.redirect('/admin/artists');
}

function editArtistForm(req, res) {
    const id = parseInt(req.params.id);
    const artist = Artist.getById(id);

    if (!artist) {
        return res.redirect('/admin/artists');
    }

    res.render('artist-form', {
        user: req.session.username,
        artist,
        error: null
    });
}

function updateArtist(req, res) {
    const id = parseInt(req.params.id);
    const name = req.body.name ? req.body.name.trim() : '';

    if (!name) {
        const artist = Artist.getById(id);
        return res.render('artist-form', {
            user: req.session.username,
            artist,
            error: 'El nombre del artista es obligatorio.'
        });
    }

    Artist.update(id, name);
    res.redirect('/admin/artists');
}

function deleteArtist(req, res) {
    const id = parseInt(req.params.id);
    Artist.delete(id);
    res.redirect('/admin/artists');
}

module.exports = {
    getArtists,
    newArtistForm,
    createArtist,
    editArtistForm,
    updateArtist,
    deleteArtist
};
