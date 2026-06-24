document.addEventListener('DOMContentLoaded', function() {
    const artistSelect = document.getElementById('artist');
    const albumSelect = document.getElementById('album');
    const albumArtistSelect = document.getElementById('artist_id');

    const commonConfig = {
        create: true,
        createOnBlur: true,
        allowEmptyOption: true,
        placeholder: 'Escribe o selecciona...',
        render: {
            no_results: function(data, escape) {
                return '<div class="no-results">No se encontró <strong>' + escape(data.input) + '</strong>. Pulsa Enter para crearlo.</div>';
            }
        }
    };

    if (artistSelect) {
        new TomSelect(artistSelect, commonConfig);
    }

    if (albumSelect) {
        new TomSelect(albumSelect, commonConfig);
    }

    if (albumArtistSelect) {
        new TomSelect(albumArtistSelect, {
            allowEmptyOption: true,
            placeholder: 'Elige un artista...'
        });
    }
});
