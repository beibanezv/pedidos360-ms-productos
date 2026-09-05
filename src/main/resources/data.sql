-- Catálogo semilla de Pedidos360 (idempotente: solo inserta si la tabla está vacía)
INSERT INTO productos (id, nombre, artistaomarca, categoria, precio_clp, stock, descripcion)
SELECT gen_random_uuid(), v.nombre, v.artista, v.categoria, v.precio, v.stock, v.descr
FROM (VALUES
  ('Horizonte', 'Trío Marejada — LP', 'vinilo', 18990, 12, 'Prensado en calidad audiófila.'),
  ('Tornamesa C-40', 'Serie artesanal', 'tornamesa', 189990, 5, 'Belt-drive, brazo de carbono.'),
  ('Auriculares Séptimo', 'Madera y cobre', 'audifono', 79990, 8, 'Cerrados, impedancia 32Ω.'),
  ('Amplificador MK II', 'Edición limitada · válvulas', 'amplificador', 249990, 3, 'Válvulas EL34, 2x40W.'),
  ('Costera EP', 'Banda del Puerto', 'vinilo', 15990, 20, 'EP en vinilo color.'),
  ('Nocturno', 'Ana Volant — LP', 'vinilo', 19990, 15, 'LP 180g con sleeve impreso.'),
  ('Tornamesa R-2', 'Portátil · belt-drive', 'tornamesa', 99990, 6, 'Batería integrada, USB out.'),
  ('Monitores Estudio 5', 'Par activo', 'audifono', 129990, 4, 'Bi-amplificados, 5".')
) AS v(nombre, artista, categoria, precio, stock, descr)
WHERE NOT EXISTS (SELECT 1 FROM productos);
