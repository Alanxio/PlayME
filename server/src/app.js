require('dotenv').config();
const express = require('express');
const session = require('express-session');
const path = require('path');
const { initialize: initDb } = require('./config/db');
const { init } = require('./config/init');
const apiRoutes = require('./routes/api');
const adminRoutes = require('./routes/admin');
const authRoutes = require('./routes/auth');
const artistRoutes = require('./routes/artist');
const albumRoutes = require('./routes/album');
const errorHandler = require('./middleware/errorHandler');
const Song = require('./models/Song');

const app = express();
const PORT = process.env.PORT || 4000;

async function startServer() {
    // Inicializar base de datos (sql.js es async)
    await initDb();

    // Inicializar admin y migraciones
    await init();

    // Configuración de vistas
    app.set('view engine', 'ejs');
    app.set('views', path.join(__dirname, 'views'));

    // Middlewares
    app.use(express.urlencoded({ extended: true }));
    app.use(express.json());
    app.use(session({
        secret: process.env.SESSION_SECRET || 'clave_por_defecto_insegura',
        resave: false,
        saveUninitialized: false,
        cookie: { secure: false, maxAge: 24 * 60 * 60 * 1000 }
    }));

    // Archivos estáticos
app.use(express.static(path.join(__dirname, '..', 'public')));
app.use('/uploads', express.static(path.join(__dirname, '..', 'uploads')));
app.use('/tmp-covers', express.static(path.join(__dirname, '..', 'uploads', 'tmp-covers')));
app.use('/vendor', express.static(path.join(__dirname, '..', 'node_modules')));

    // Rutas
    app.use('/', authRoutes);
    app.use('/admin', adminRoutes);
    app.use('/admin/artists', artistRoutes);
    app.use('/admin/albums', albumRoutes);
    app.use('/', apiRoutes);

    // Página de inicio
    app.get('/', (req, res) => {
        res.render('index', {
            stats: {
                songs: Song.count(),
                artists: Song.getArtists().length,
                albums: Song.getAlbums().length
            }
        });
    });

    // Manejo de errores
    app.use(errorHandler);

    app.listen(PORT, '0.0.0.0', () => {
        const os = require('os');
        console.log(`\n🎵 PlayME MediaServer iniciado en puerto ${PORT}`);
        console.log(`\nDirecciones disponibles:`);
        console.log(`  http://localhost:${PORT}`);

        Object.values(os.networkInterfaces()).forEach(iface => {
            iface.forEach(addr => {
                if (addr.family === 'IPv4' && !addr.internal) {
                    console.log(`  http://${addr.address}:${PORT}`);
                }
            });
        });

        console.log(`\nPanel admin: http://localhost:${PORT}/admin`);
        console.log(`Usuario: ${process.env.ADMIN_USER || 'admin'}`);
        console.log(`\nRecuerda colocar tu cover por defecto en: uploads/covers/${process.env.DEFAULT_COVER || 'default.jpg'}`);
    });
}

startServer().catch(err => {
    console.error('Error iniciando el servidor:', err);
    process.exit(1);
});
