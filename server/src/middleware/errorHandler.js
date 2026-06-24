function errorHandler(err, req, res, next) {
    console.error('Error:', err.message);

    if (err.code === 'LIMIT_FILE_SIZE') {
        return res.status(400).render('error', { message: 'El archivo es demasiado grande.' });
    }

    if (err.message && err.message.includes('Solo se permiten')) {
        return res.status(400).render('error', { message: err.message });
    }

    res.status(500).render('error', { message: 'Ha ocurrido un error inesperado.' });
}

module.exports = errorHandler;
