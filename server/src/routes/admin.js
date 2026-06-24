const express = require('express');
const router = express.Router();
const requireAuth = require('../middleware/requireAuth');
const { uploadSongFiles, uploadPreviewAudio } = require('../middleware/upload');
const adminController = require('../controllers/adminController');

router.get('/', requireAuth, adminController.getDashboard);
router.get('/songs', requireAuth, adminController.getSongs);
router.get('/songs/new', requireAuth, adminController.newSongForm);
router.post('/songs/preview-metadata', requireAuth, uploadPreviewAudio, adminController.previewMetadata);
router.post('/songs/ensure-artist-album', requireAuth, adminController.ensureArtistAlbum);
router.post('/songs', requireAuth, uploadSongFiles, adminController.createSong);
router.get('/songs/:id/edit', requireAuth, adminController.editSongForm);
router.post('/songs/:id/edit', requireAuth, uploadSongFiles, adminController.updateSong);
router.post('/songs/:id/delete', requireAuth, adminController.deleteSong);

module.exports = router;
