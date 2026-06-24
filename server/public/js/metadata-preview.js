(function() {
    const form = document.querySelector('.song-form');
    if (!form) return;

    const audioInput = form.querySelector('input[type="file"][name="audio"]');
    const titleInput = form.querySelector('input[name="title"]');
    const artistInput = form.querySelector('input[name="artist"]');
    const albumInput = form.querySelector('input[name="album"]');
    const durationInput = form.querySelector('input[name="duration"]');
    const coverInput = form.querySelector('input[type="file"][name="cover"]');
    const submitBtn = form.querySelector('button[type="submit"]');

    const statusContainer = document.createElement('div');
    statusContainer.className = 'metadata-status';
    audioInput.parentNode.appendChild(statusContainer);

    const previewContainer = document.createElement('div');
    previewContainer.className = 'cover-preview-container';
    if (coverInput) {
        coverInput.parentNode.appendChild(previewContainer);
    }

    const fieldMap = [
        { key: 'title', input: titleInput, label: 'Título' },
        { key: 'artist', input: artistInput, label: 'Artista' },
        { key: 'album', input: albumInput, label: 'Álbum' },
        { key: 'duration', input: durationInput, label: 'Duración' }
    ];

    function resetFieldStyles() {
        fieldMap.forEach(({ input }) => {
            if (!input) return;
            input.classList.remove('field-filled', 'field-missing');
        });
    }

    function setFieldStatus(input, filled) {
        if (!input) return;
        input.classList.remove('field-filled', 'field-missing');
        input.classList.add(filled ? 'field-filled' : 'field-missing');

        // También aplicar al wrapper de Tom Select si existe
        if (input.tomselect && input.tomselect.wrapper) {
            input.tomselect.wrapper.classList.remove('field-filled', 'field-missing');
            input.tomselect.wrapper.classList.add(filled ? 'field-filled' : 'field-missing');
        }
    }

    function updateStatus(found, missing) {
        let html = '';
        if (found.length > 0) {
            html += `<div class="status-found">✅ Etiquetas encontradas: ${found.join(', ')}</div>`;
        }
        if (missing.length > 0) {
            html += `<div class="status-missing">⚠️ Etiquetas no encontradas: ${missing.join(', ')}</div>`;
        }
        if (found.length === 0 && missing.length === 0) {
            html = '<div class="status-missing">⚠️ No se encontraron etiquetas en el archivo.</div>';
        }
        statusContainer.innerHTML = html;
    }

    function showPreviewCover(url) {
        if (!previewContainer) return;
        previewContainer.innerHTML = `
            <div class="preview-cover">
                <small>Carátula encontrada en los metadatos:</small>
                <img src="${url}" alt="Carátula preview">
                <small>Si quieres usarla, no subas otra carátula.</small>
            </div>
        `;
    }

    function clearPreviewCover() {
        if (!previewContainer) return;
        previewContainer.innerHTML = '';
    }

    async function ensureArtistAlbum(artist, album) {
        const response = await fetch('/admin/songs/ensure-artist-album', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ artist, album })
        });

        if (!response.ok) {
            throw new Error('Error creando/buscando artista y álbum');
        }

        return await response.json();
    }

    audioInput.addEventListener('change', async function() {
        const file = audioInput.files[0];
        if (!file) return;

        resetFieldStyles();
        clearPreviewCover();
        statusContainer.innerHTML = '<div class="status-loading">📀 Leyendo metadatos...</div>';
        if (submitBtn) submitBtn.disabled = true;

        const formData = new FormData();
        formData.append('audio', file);

        try {
            const response = await fetch('/admin/songs/preview-metadata', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Error desconocido');
            }

            const data = await response.json();

            // Asegurar que artista/álbum existan en la base de datos
            const resolved = await ensureArtistAlbum(data.artist, data.album);

            const found = [];
            const missing = [];

            fieldMap.forEach(({ key, input, label }) => {
                if (!input) return;
                let value = data[key];

                // Para artista y álbum, usamos el nombre normalizado de la base de datos
                if (key === 'artist' && resolved.artistName) value = resolved.artistName;
                if (key === 'album' && resolved.albumName) value = resolved.albumName;

                if (value && String(value).trim() !== '' && String(value) !== '0') {
                    const currentValue = input.tomselect ? input.tomselect.getValue() : input.value;
                    if (!currentValue || String(currentValue).trim() === '') {
                        if (input.tomselect) {
                            input.tomselect.setValue(value);
                        } else {
                            input.value = value;
                        }
                    }
                    setFieldStatus(input, true);
                    found.push(label);
                } else {
                    setFieldStatus(input, false);
                    missing.push(label);
                }
            });

            updateStatus(found, missing);

            if (data.coverUrl) {
                showPreviewCover(data.coverUrl);
            }
        } catch (err) {
            console.error('Error leyendo metadatos:', err);
            statusContainer.innerHTML = `<div class="status-error">❌ Error leyendo metadatos: ${err.message}</div>`;
        } finally {
            if (submitBtn) submitBtn.disabled = false;
        }
    });
})();
