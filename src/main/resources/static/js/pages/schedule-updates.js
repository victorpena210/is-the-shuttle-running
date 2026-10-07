(() => {
  const upload = document.getElementById('screenshot-upload');
  upload?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const status = document.getElementById('upload-status');
    const files = document.getElementById('screenshots').files;
    if (files.length > 3 || [...files].some(f => f.size > 5 * 1024 * 1024) || [...files].reduce((n,f) => n+f.size,0) > 12*1024*1024) {
      status.textContent = 'Choose up to 3 screenshots, 5 MB each and 12 MB total.'; return;
    }
    const button = upload.querySelector('button'); button.disabled = true;
    status.textContent = 'Reading your screenshots. This may take up to a minute…';
    try {
      // Header token keeps CSRF enabled without putting the token in a URL or parsing an unauthenticated upload.
      const response = await fetch(upload.action, { method: 'POST', credentials:'same-origin', body: new FormData(upload),
        headers: { [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content } });
      if (response.redirected) { window.location.assign(response.url); return; }
      if (!response.ok) throw new Error('Could not read the upload. Check its size or sign in again.');
      const html = await response.text(); document.open(); document.write(html); document.close();
    } catch (error) { status.textContent = error.message || 'Upload failed. Try again or enter your hours manually.'; button.disabled=false; }
  });
  const rows = document.getElementById('schedule-rows');
  function renumber() {
    rows?.querySelectorAll('.schedule-row').forEach((row,i) => {
      row.querySelector('legend').textContent = `Work entry ${i+1}`;
      row.querySelectorAll('input').forEach(input => {
        input.name = input.name.replace(/rows\[\d+\]/,`rows[${i}]`);
        input.removeAttribute('id');
      });
    });
  }
  function toggle(row) {
    const off = row.querySelector('input[type="checkbox"]').checked;
    row.querySelectorAll('input[type="time"]').forEach(input => { input.disabled=off; });
  }
  rows?.querySelectorAll('.schedule-row').forEach(toggle);
  rows?.addEventListener('change', event => { if(event.target.type==='checkbox') toggle(event.target.closest('.schedule-row')); });
  rows?.addEventListener('click', event => {
    if (!event.target.closest('.remove-row')) return;
    const all=rows.querySelectorAll('.schedule-row');
    if(all.length===1) { all[0].querySelectorAll('input').forEach(i=>{if(i.type==='checkbox') i.checked=false;else if(i.type!=='hidden') i.value='';}); toggle(all[0]); }
    else event.target.closest('.schedule-row').remove();
    renumber();
  });
  document.getElementById('add-row')?.addEventListener('click', () => {
    if(rows.children.length>=60) return;
    const row=rows.querySelector('.schedule-row').cloneNode(true);
    row.querySelectorAll('input').forEach(input => { if(input.type==='checkbox') input.checked=false; else if(input.type!=='hidden') input.value=''; });
    rows.append(row); renumber(); toggle(row); row.querySelector('input[type="date"]').focus();
  });
})();
