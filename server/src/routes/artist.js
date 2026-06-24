const express = require('express');
const router = express.Router();
const requireAuth = require('../middleware/requireAuth');
const artistController = require('../controllers/artistController');

router.get('/', requireAuth, artistController.getArtists);
router.get('/new', requireAuth, artistController.newArtistForm);
router.post('/', requireAuth, artistController.createArtist);
router.get('/:id/edit', requireAuth, artistController.editArtistForm);
router.post('/:id/edit', requireAuth, artistController.updateArtist);
router.post('/:id/delete', requireAuth, artistController.deleteArtist);

module.exports = router;
