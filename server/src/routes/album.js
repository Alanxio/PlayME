const express = require('express');
const router = express.Router();
const requireAuth = require('../middleware/requireAuth');
const { uploadCover } = require('../middleware/upload');
const albumController = require('../controllers/albumController');

router.get('/', requireAuth, albumController.getAlbums);
router.get('/new', requireAuth, albumController.newAlbumForm);
router.post('/', requireAuth, uploadCover, albumController.createAlbum);
router.get('/:id/edit', requireAuth, albumController.editAlbumForm);
router.post('/:id/edit', requireAuth, uploadCover, albumController.updateAlbum);
router.post('/:id/delete', requireAuth, albumController.deleteAlbum);

module.exports = router;
