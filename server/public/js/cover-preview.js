(function() {
    document.querySelectorAll('input[type="file"][data-preview-target]').forEach(input => {
        const targetSelector = input.getAttribute('data-preview-target');
        const previewContainer = document.querySelector(targetSelector);
        if (!previewContainer) return;

        input.addEventListener('change', function() {
            previewContainer.innerHTML = '';

            const file = input.files[0];
            if (!file) return;

            if (!file.type.startsWith('image/')) {
                previewContainer.innerHTML = '<div class="alert alert-error">El archivo seleccionado no es una imagen.</div>';
                return;
            }

            const reader = new FileReader();
            reader.onload = function(e) {
                previewContainer.innerHTML = `
                    <div class="preview-cover">
                        <small>Nueva carátula seleccionada:</small>
                        <img src="${e.target.result}" alt="Preview de cover">
                    </div>
                `;
            };
            reader.readAsDataURL(file);
        });
    });
})();
