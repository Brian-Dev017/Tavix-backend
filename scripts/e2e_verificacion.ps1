# =============================================================
#  Verificación E2E — IGV, reglas de caja, ruteo cocina, reportes
#  Contra el backend real en http://localhost:8081 + MySQL.
#  Idempotente: limpia sus datos al inicio y al final.
# =============================================================
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081"
$env:MYSQL_PWD = "root"
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
function Sql($q) { & $mysql -uroot -D restaurante -N -e $q }
function SqlScalar($q) { (& $mysql -uroot -D restaurante -N -e $q | Select-Object -Last 1).ToString().Trim() }
$script:OK = 0; $script:FAIL = 0
function Check($name, $cond, $detail) {
    if ($cond) { $script:OK++; Write-Host ("  PASS  {0}" -f $name) -ForegroundColor Green }
    else { $script:FAIL++; Write-Host ("  FAIL  {0}  ({1})" -f $name, $detail) -ForegroundColor Red }
}
function Login($u, $p) {
    (Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType "application/json" -Body (@{usuario=$u;contrasena=$p}|ConvertTo-Json)).data.accessToken
}
function Api($method, $path, $token, $body) {
    $a = @{ Uri="$base$path"; Method=$method; Headers=@{Authorization="Bearer $token"} }
    if ($body) { $a.ContentType="application/json"; $a.Body=($body|ConvertTo-Json) }
    Invoke-RestMethod @a
}
function ApiStatus($method, $path, $token, $body) {
    try { Api $method $path $token $body | Out-Null; 200 }
    catch { [int]$_.Exception.Response.StatusCode }
}

Write-Host "`n=== A) LOGIN ===" -ForegroundColor Cyan
$adminTok = Login "admin" "admin123"
Check "login admin" ([bool]$adminTok) "sin token"
Sql "UPDATE usuario SET contrasena_hash=(SELECT h FROM (SELECT contrasena_hash h FROM usuario WHERE usuario='admin') x) WHERE usuario IN ('cajero','mesero');" | Out-Null
$cajeroId = SqlScalar "SELECT id FROM usuario WHERE usuario='cajero' LIMIT 1"
$meseroId = SqlScalar "SELECT id FROM usuario WHERE usuario='mesero' LIMIT 1"
$cajTok = Login "cajero" "admin123"
Check "login cajero" ([bool]$cajTok) "sin token"

# ---- Limpieza previa (datos E2E y sesiones huérfanas) ----
Sql "DELETE FROM comprobante_venta WHERE pedido_id IN (SELECT id FROM pedido WHERE observaciones IN ('E2E_IGV','E2E_COCINA'));" | Out-Null
Sql "DELETE dp FROM detalle_pedido dp JOIN pedido p ON p.id=dp.pedido_id WHERE p.observaciones IN ('E2E_IGV','E2E_COCINA');" | Out-Null
Sql "DELETE FROM pedido WHERE observaciones IN ('E2E_IGV','E2E_COCINA');" | Out-Null
Sql "DELETE FROM sesion_mesa WHERE mesero_id=$meseroId AND cerrada_en IS NULL AND id NOT IN (SELECT sesion_mesa_id FROM pedido WHERE sesion_mesa_id IS NOT NULL);" | Out-Null
Sql "UPDATE mesa SET estado='DISPONIBLE' WHERE tipo='SALON' AND id NOT IN (SELECT mesa_id FROM sesion_mesa WHERE cerrada_en IS NULL AND mesa_id IS NOT NULL);" | Out-Null
Sql "DELETE FROM comprobante_venta WHERE arqueo_caja_id IN (SELECT id FROM arqueo_caja WHERE cajero_id=$cajeroId AND DATE(apertura_en)=CURDATE());" | Out-Null
Sql "DELETE FROM arqueo_caja WHERE cajero_id=$cajeroId AND DATE(apertura_en)=CURDATE();" | Out-Null

Write-Host "`n=== B) REGLAS DE CAJA (apertura) ===" -ForegroundColor Cyan
$st = ApiStatus Post "/api/caja/arqueos/abrir" $adminTok @{cajeroId=0;montoApertura=100}
Check "admin NO puede aperturar (403)" ($st -eq 403) "status=$st"
$ab = Api Post "/api/caja/arqueos/abrir" $cajTok @{cajeroId=[int]$cajeroId;montoApertura=100}
$arqueoId = $ab.data.id
Check "cajero apertura OK" ([bool]$arqueoId) "sin id"
$st2 = ApiStatus Post "/api/caja/arqueos/abrir" $cajTok @{cajeroId=[int]$cajeroId;montoApertura=100}
Check "2da apertura con caja abierta: bloqueada" ($st2 -ge 400) "status=$st2"

Write-Host "`n=== C) COBRO + IGV CORRECTO ===" -ForegroundColor Cyan
$cat = SqlScalar "SELECT id FROM categoria WHERE activo=1 ORDER BY id LIMIT 1"
Sql "DELETE FROM producto WHERE codigo IN ('E2E-VASO','E2E-GAS','E2E-NUBE');" | Out-Null
Sql "INSERT INTO producto (categoria_id,nombre,codigo,precio,afectacion_igv,requiere_cocina,disponible) VALUES ($cat,'E2E VASOS','E2E-VASO',6.20,'GRAVADO',1,1),($cat,'E2E GASEOSA','E2E-GAS',6.80,'GRAVADO',0,1),($cat,'E2E NUBE','E2E-NUBE',2.30,'GRAVADO',1,1);" | Out-Null
$pVaso = SqlScalar "SELECT id FROM producto WHERE codigo='E2E-VASO'"
$pGas  = SqlScalar "SELECT id FROM producto WHERE codigo='E2E-GAS'"
$pNube = SqlScalar "SELECT id FROM producto WHERE codigo='E2E-NUBE'"
$mesa = SqlScalar "SELECT id FROM mesa WHERE estado='DISPONIBLE' AND tipo='SALON' ORDER BY id LIMIT 1"
$ses = SqlScalar "INSERT INTO sesion_mesa (mesa_id,mesero_id) VALUES ($mesa,$meseroId); SELECT LAST_INSERT_ID();"
$ped = SqlScalar "INSERT INTO pedido (sesion_mesa_id,estado,observaciones) VALUES ($ses,'LISTO','E2E_IGV'); SELECT LAST_INSERT_ID();"
Sql "INSERT INTO detalle_pedido (pedido_id,producto_id,cantidad,precio_unitario,estado) VALUES ($ped,$pVaso,2,6.20,'LISTO'),($ped,$pGas,1,6.80,'LISTO'),($ped,$pNube,1,2.30,'LISTO');" | Out-Null

$comp = Api Post "/api/caja/comprobante" $cajTok @{pedidoId=[int]$ped;tipoComprobanteId="T";metodoPago="EFECTIVO";efectivoRecibido=21.50}
$d = $comp.data
Write-Host ("  -> subtotal={0} igv={1} total={2} vuelto={3}" -f $d.subtotal,$d.igv,$d.total,$d.vuelto)
Check "IGV correcto = 3.28" ([decimal]$d.igv -eq 3.28) "igv=$($d.igv)"
Check "Base imponible = 18.22" ([decimal]$d.subtotal -eq 18.22) "subtotal=$($d.subtotal)"
Check "Total = 21.50" ([decimal]$d.total -eq 21.50) "total=$($d.total)"
Check "Vuelto = 0.00" ([decimal]$d.vuelto -eq 0.00) "vuelto=$($d.vuelto)"

Write-Host "`n=== D) REPORTES ===" -ForegroundColor Cyan
$hoy = (Get-Date).ToString("yyyy-MM-dd")
$vpf = Api Get "/api/reportes/ventas-por-fecha?desde=$hoy&hasta=$hoy" $adminTok $null
$rowHoy = $vpf.data | Select-Object -First 1
Check "ventas-por-fecha IGV >= 3.28" ([decimal]$rowHoy.igv -ge 3.28) "igv=$($rowHoy.igv)"
$pm = Api Get "/api/reportes/por-metodo?desde=$hoy&hasta=$hoy" $adminTok $null
$ef = $pm.data | Where-Object { $_.metodo -eq "EFECTIVO" } | Select-Object -First 1
Check "por-metodo EFECTIVO >= 21.50" ([decimal]$ef.total -ge 21.50) "total=$($ef.total)"
$pv = Api Get "/api/reportes/productos-vendidos?desde=$hoy&hasta=$hoy&orden=cantidad&dir=desc" $adminTok $null
$vaso = $pv.data | Where-Object { $_.producto -eq "E2E VASOS" } | Select-Object -First 1
Check "productos-vendidos VASOS cantidad=2" ([int]$vaso.cantidad -eq 2) "cant=$($vaso.cantidad)"
$res = Api Get "/api/reportes/resumen?desde=$hoy&hasta=$hoy" $adminTok $null
Check "resumen ingresos >= 21.50" ([decimal]$res.data.ingresos -ge 21.50) "ingresos=$($res.data.ingresos)"
$hist = Api Get "/api/reportes/historial-detallado?page=0&size=10" $adminTok $null
$ih = $hist.data.items | Where-Object { $_.pedidoId -eq [int]$ped } | Select-Object -First 1
Check "historial-detallado trae cajero+canal" ([bool]$ih.cajero -and [bool]$ih.canal) "cajero=$($ih.cajero) canal=$($ih.canal)"
$g = ApiStatus Post "/api/reportes/gastos" $adminTok @{fecha=$hoy;categoria="INSUMOS";descripcion="E2E test";monto=50}
Check "registrar gasto (201)" ($g -eq 200 -or $g -eq 201) "status=$g"
$cd = Api Get "/api/reportes/caja-diaria?fecha=$hoy" $adminTok $null
Check "caja-diaria efectivo >= 21.50" ([decimal]$cd.data.totalEfectivo -ge 21.50) "ef=$($cd.data.totalEfectivo)"
Check "caja-diaria gastos >= 50" ([decimal]$cd.data.gastos -ge 50) "gastos=$($cd.data.gastos)"

Write-Host "`n=== E) RUTEO DE COCINA ===" -ForegroundColor Cyan
$mesaC = SqlScalar "SELECT id FROM mesa WHERE estado='DISPONIBLE' AND tipo='SALON' ORDER BY id LIMIT 1"
$sesC = SqlScalar "INSERT INTO sesion_mesa (mesa_id,mesero_id) VALUES ($mesaC,$meseroId); SELECT LAST_INSERT_ID();"
$pedC = SqlScalar "INSERT INTO pedido (sesion_mesa_id,estado,observaciones) VALUES ($sesC,'ABIERTO','E2E_COCINA'); SELECT LAST_INSERT_ID();"
$pFood = SqlScalar "SELECT id FROM producto WHERE requiere_cocina=1 AND disponible=1 ORDER BY id LIMIT 1"
$pBeb  = SqlScalar "SELECT id FROM producto WHERE requiere_cocina=0 AND disponible=1 ORDER BY id LIMIT 1"
$rFood = Api Post "/api/pedidos/$pedC/items" $adminTok @{productoId=[int]$pFood;cantidad=1}
$rBeb  = Api Post "/api/pedidos/$pedC/items" $adminTok @{productoId=[int]$pBeb;cantidad=1}
Check "item de cocina queda PENDIENTE" ($rFood.data.estado -eq "PENDIENTE") "estado=$($rFood.data.estado)"
Check "bebida queda LISTO (no cocina)" ($rBeb.data.estado -eq "LISTO") "estado=$($rBeb.data.estado)"
$enCola = SqlScalar "SELECT COUNT(*) FROM v_cola_cocina WHERE pedido_id=$pedC"
Check "solo 1 item en cola (la bebida NO entra)" ($enCola -eq "1") "encola=$enCola"
$estPed = SqlScalar "SELECT estado FROM pedido WHERE id=$pedC"
Check "pedido en EN_COCINA" ($estPed -eq "EN_COCINA") "estado=$estPed"

Write-Host "`n=== F) ADMIN CIERRA CAJA + REGLA 'UNA POR DIA' ===" -ForegroundColor Cyan
$cierre = ApiStatus Post "/api/caja/arqueos/$arqueoId/cerrar" $adminTok @{montoCierre=121.50;notas="cierre admin E2E"}
Check "admin SI puede cerrar caja (200)" ($cierre -eq 200) "status=$cierre"
$st3 = ApiStatus Post "/api/caja/arqueos/abrir" $cajTok @{cajeroId=[int]$cajeroId;montoApertura=100}
Check "reapertura mismo dia tras cierre: 409 (una por dia)" ($st3 -eq 409) "status=$st3"

Write-Host "`n=== LIMPIEZA ===" -ForegroundColor Cyan
Sql "DELETE FROM comprobante_venta WHERE pedido_id IN (SELECT id FROM pedido WHERE observaciones IN ('E2E_IGV','E2E_COCINA'));" | Out-Null
Sql "DELETE dp FROM detalle_pedido dp JOIN pedido p ON p.id=dp.pedido_id WHERE p.observaciones IN ('E2E_IGV','E2E_COCINA');" | Out-Null
$sess = SqlScalar "SELECT COALESCE(GROUP_CONCAT(sesion_mesa_id),'0') FROM pedido WHERE observaciones IN ('E2E_IGV','E2E_COCINA')"
Sql "DELETE FROM pedido WHERE observaciones IN ('E2E_IGV','E2E_COCINA');" | Out-Null
Sql "DELETE FROM sesion_mesa WHERE id IN ($sess);" | Out-Null
Sql "UPDATE mesa SET estado='DISPONIBLE' WHERE tipo='SALON' AND id NOT IN (SELECT mesa_id FROM sesion_mesa WHERE cerrada_en IS NULL AND mesa_id IS NOT NULL);" | Out-Null
Sql "DELETE FROM producto WHERE codigo IN ('E2E-VASO','E2E-GAS','E2E-NUBE');" | Out-Null
Sql "DELETE FROM gasto WHERE descripcion='E2E test';" | Out-Null
Sql "DELETE FROM comprobante_venta WHERE arqueo_caja_id=$arqueoId;" | Out-Null
Sql "DELETE FROM arqueo_caja WHERE id=$arqueoId;" | Out-Null

Write-Host ("`n================ RESULTADO: {0} PASS / {1} FAIL ================" -f $script:OK,$script:FAIL) -ForegroundColor Yellow
if ($script:FAIL -eq 0) { Write-Host ">>> TODAS LAS PRUEBAS E2E PASARON <<<" -ForegroundColor Green } else { Write-Host ">>> HAY FALLOS <<<" -ForegroundColor Red }
