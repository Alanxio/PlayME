const bcrypt = require('bcryptjs');
const { getDb } = require('../config/db');

function showLogin(req, res) {
    if (req.session.userId) {
        return res.redirect('/admin');
    }
    res.render('login', { error: null });
}

function login(req, res) {
    const db = getDb();
    const { username, password } = req.body;

    const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (!user || !bcrypt.compareSync(password, user.password_hash)) {
        return res.render('login', { error: 'Usuario o contraseña incorrectos' });
    }

    req.session.userId = user.id;
    req.session.username = user.username;
    res.redirect('/admin');
}

function logout(req, res) {
    req.session.destroy(() => {
        res.redirect('/login');
    });
}

module.exports = {
    showLogin,
    login,
    logout
};
