# Windows PowerShell. Use the same ticket path before and after upgrading the dev stack.
param(
    [Parameter(Mandatory)][string]$Ticket,
    [switch]$BeforeUpgrade
)
$ErrorActionPreference='Stop'
function callApi($method,$route,$token,$body) {
    $argsHttp=@{Method=$method;Uri="http://localhost:8080/api/$route";TimeoutSec=12}
    if ($token) { $argsHttp.Headers=@{Authorization="Bearer $token"} }
    if ($body) { $argsHttp.ContentType='application/json'; $argsHttp.Body=($body | ConvertTo-Json -Compress) }
    Invoke-RestMethod @argsHttp
}
function need($value,$label) { if (!$value) { throw $label }; Write-Output "PASS $label" }
function claims($token) {
    $s=$token.Split('.')[1].Replace('-','+').Replace('_','/')
    $s=$s.PadRight($s.Length+((4-$s.Length%4)%4),'=')
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s)) | ConvertFrom-Json
}
function register($zone) {
    $key=[guid]::NewGuid().ToString()
    $body=@{email="tz-$key@example.invalid";password=$key;fullName='Timezone QA'}
    if ($zone) {$body.timezone=$zone}
    callApi POST auth/register $null $body
}
function expense($token,$instant) {
    $w=(callApi GET wallets $token)[0]; $c=((callApi GET categories $token) | Where-Object flow -eq 'EXPENSE' | Select-Object -First 1)
    callApi POST transactions $token @{walletId=$w.id;categoryId=$c.id;amount=1700;type='EXPENSE';occurredAt=$instant;merchant='Timezone QA'}
}
function periods($token,$windowsZone) {
    $today=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow,$windowsZone)
    $month=$today.ToString('yyyy-MM'); $left=[DateTime]::DaysInMonth($today.Year,$today.Month)-$today.Day+1
    $budget=callApi GET budgets/current $token; $report=callApi GET stats/cashflow-summary $token
    need ($budget.periodKey -eq $month -and $budget.daysLeft -eq $left -and $report.period -eq $month) "budget and report $month, daysLeft $left ($windowsZone)"
}
if ($BeforeUpgrade) {
    if (Test-Path -LiteralPath $Ticket) { throw 'Choose an unused QA ticket path' }
    $old=register $null
    need ($null -eq (claims $old.accessToken).tz) 'genuine pre-upgrade token'
    [pscredential]::new('legacyQA',(ConvertTo-SecureString $old.accessToken -AsPlainText -Force)) | Export-Clixml $ticket
    exit
}
$old=(Import-Clixml $ticket).GetNetworkCredential().Password
$a=register $null
$b=register 'America/Los_Angeles'
$vn='Asia/Ho_Chi_Minh'
need ((claims $a.accessToken).tz -eq $vn -and (claims $b.accessToken).tz -eq 'America/Los_Angeles') 'default and supplied signup zone in issued tokens'
$at='2026-11-27T17:15:00Z'
$x=expense $a.accessToken $at
$y=expense $b.accessToken $at
$z=expense $old $at
need ($x.occurredDate -eq '2026-11-28' -and $z.occurredDate -eq $x.occurredDate -and $y.occurredDate -eq '2026-11-27') 'ledger dates for Vietnam, Los Angeles and legacy token'
periods $a.accessToken 'SE Asia Standard Time'
periods $b.accessToken 'Pacific Standard Time'
foreach($s in @(@($a,'2026-11-28'),@($b,'2026-11-27'))) {
    $t=$s[0].accessToken; $date=$s[1]; $until=(Get-Date).AddSeconds(35)
    do { $report=callApi GET "stats/transactions?period=2026-11&date=$date&type=EXPENSE" $t
        if ($report.filteredTxnCount -eq 1) {break}
        Start-Sleep -Milliseconds 400
    } while ((Get-Date) -lt $until)
    need ($report.filteredTxnCount -eq 1) "projected report $date"
}
foreach ($invalid in 'bad/zone','UTC','+07:00') {
    try { $null=callApi PATCH users/me $b.accessToken @{timezone=$invalid}; throw 'Expected invalid zone' }
    catch { if ($_.Exception.Response.StatusCode.value__ -ne 400) {throw} }
}
try {$null=callApi GET users/me $null;throw 'Expected unauthorized'} catch {if ($_.Exception.Response.StatusCode.value__ -ne 401) {throw}}
need ((callApi GET users/me $b.accessToken).timezone -eq 'America/Los_Angeles') 'invalid timezone leaves profile intact'
$changed=callApi PATCH users/me $b.accessToken @{timezone=$vn}
need ($changed.userId -eq $b.userId -and (claims $changed.accessToken).tz -eq $vn -and (claims $changed.accessToken).exp -eq (claims $b.accessToken).exp) 'PATCH updates zone without extending access lifetime'
need ((callApi GET "transactions/$($y.id)" $changed.accessToken).occurredDate -eq '2026-11-27') 'old business date retained'
need ((expense $changed.accessToken $at).occurredDate -eq '2026-11-28') 'subsequent transaction uses updated zone'
$refreshed=callApi POST auth/refresh $null @{refreshToken=$b.refreshToken}
need ((claims $refreshed.accessToken).tz -eq $vn) 'refresh reads persisted zone'
$far=callApi PATCH users/me $refreshed.accessToken @{timezone='Pacific/Kiritimati'}
periods $far.accessToken 'Line Islands Standard Time'
