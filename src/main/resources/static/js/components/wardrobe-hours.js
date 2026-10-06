export function initializeWardrobeHours() {
    document.querySelectorAll('[data-wardrobe-season-form]').forEach(form => {
        form.querySelectorAll('[data-wardrobe-preset]').forEach(button => {
            button.addEventListener('click', () => {
                form.elements.namedItem('label').value = button.dataset.wardrobePreset;
                form.elements.namedItem('closingTime').value = button.dataset.closingTime;
                form.elements.namedItem('from').focus();
            });
        });
    });
}
