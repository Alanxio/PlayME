const express = require('express');
const router = express.Router();
const apiController = require('../controllers/apiController');

router.get('/catalog', apiController.getCatalog);
router.get('/catalog/count', apiController.getCatalogCount);
router.get('/catalog/artists', apiController.getArtists);
router.get('/catalog/albums', apiController.getAlbums);
router.get('/song/:id', apiController.getSong);
router.get('/cover/:filename', apiController.getCover);
router.get('/stream/:id', apiController.streamSong);

module.exports = router;
