"""Monta os arquivos dos cenarios de teste do CONCILIA (entrada + resultado esperado).

Os VALORES esperados foram calculados a mao (ver comentarios); este script so posiciona os
campos nas colunas certas do layout, para nao errar contagem de colunas digitando 150 bytes.
Rode: python3 montar-cenarios.py
"""
import os

AQUI = os.path.dirname(os.path.abspath(__file__))

def c(v):  # centavos
    return round(v * 100)

# ---------- layout MOVIMENTO (150) ----------
def h(data_ref, gerado):
    return ("H" + data_ref + gerado + "PIX-API".ljust(10)).ljust(150)

def conta(id_, chave, titular, saldo):
    return ("C" + f"{id_:010d}" + chave.ljust(77) + titular.ljust(30) + f"{c(saldo):015d}").ljust(150)

def lanc(id_, data, hora, nat, tipo, valor, tx=""):
    return ("L" + f"{id_:010d}" + data + hora + nat + tipo.ljust(12) + f"{c(valor):015d}" + tx.ljust(36)).ljust(150)

def trailer(qc, ql, cred, deb):
    return ("T" + f"{qc:09d}" + f"{ql:09d}" + f"{c(cred):017d}" + f"{c(deb):017d}").ljust(150)

# ---------- layout RESULTADO (200) ----------
def s(v, n=15):  # S9(13)V99 SIGN LEADING SEPARATE
    return ("+" if v >= 0 else "-") + f"{abs(c(v)):0{n}d}"

def res_a(id_, chave, titular, abertura, cred, deb, posterior, calc, api, dif, situacao):
    return ("A" + f"{id_:010d}" + chave[:30].ljust(30) + titular[:30].ljust(30) + s(abertura)
            + f"{c(cred):015d}" + f"{c(deb):015d}" + s(posterior) + s(calc) + f"{c(api):015d}"
            + s(dif) + situacao.ljust(12)).ljust(200).rstrip()

def res_r(qc, ql, aval, div, cred_dia, deb_dia, rc, situacao, motivo=""):
    return ("R" + "20261025" + "20261026030005" + "20261026030007" + f"{qc:09d}" + f"{ql:09d}"
            + f"{aval:09d}" + f"{div:09d}" + f"{c(cred_dia):017d}" + f"{c(deb_dia):017d}" + f"{rc:02d}"
            + situacao.ljust(10) + motivo.ljust(60)).ljust(200).rstrip()

TXA, TXB, TXC = ("aaaaaaaa-0000-4000-8000-00000000000" + d for d in "abc")

# Razao comum aos cenarios (data de referencia 25/10/2026):
#  conta 1 Ana:   abertura 1000,00 em 20/10
#  conta 2 Bruno: abertura  250,00 em 20/10
#  conta 3:       abertura   75,00 em 21/10, encerrada em 23/10 (sai 75,00) -> sem registro C
#  conta 4 Carla: abertura   50,00 em 22/10
#  dia 25: Pix A 1->2 de 120,50 (10:15) | Pix B 2->1 de 10,00 (15:30)
#  dia 26 01:30 (apos o dia, antes da extracao): Pix C 1->2 de 20,00
RAZAO = [
    lanc(1, "20261020", "090000", "C", "ABERTURA", 1000.00),
    lanc(2, "20261020", "090100", "C", "ABERTURA", 250.00),
    lanc(3, "20261021", "100000", "C", "ABERTURA", 75.00),
    lanc(4, "20261022", "110000", "C", "ABERTURA", 50.00),
    lanc(3, "20261023", "120000", "D", "ENCERRAMENTO", 75.00),
    lanc(1, "20261025", "101500", "D", "PIX_ENVIADO", 120.50, TXA),
    lanc(2, "20261025", "101500", "C", "PIX_RECEBIDO", 120.50, TXA),
    lanc(2, "20261025", "153000", "D", "PIX_ENVIADO", 10.00, TXB),
    lanc(1, "20261025", "153000", "C", "PIX_RECEBIDO", 10.00, TXB),
    lanc(1, "20261026", "013000", "D", "PIX_ENVIADO", 20.00, TXC),
    lanc(2, "20261026", "013000", "C", "PIX_RECEBIDO", 20.00, TXC),
]
# Totais do razao: creditos 1000+250+75+50+120,50+10+20 = 1525,50 | debitos 75+120,50+10+20 = 225,50
CRED, DEB = 1525.50, 225.50
HEADER = h("20261025", "20261026030005")

# Saldo esperado (abertura + creditos do dia - debitos do dia + posterior):
#  1: 1000 + 10 - 120,50 - 20 = 869,50 | 2: 250 + 120,50 - 10 + 20 = 380,50 | 3: 0 | 4: 50
CONTAS_OK = [
    conta(1, "ana@pix.com", "Ana Souza", 869.50),
    conta(2, "bruno@pix.com", "Bruno Lima", 380.50),
    conta(4, "carla@pix.com", "Carla Dias", 50.00),
]
RESULTADO_OK = [
    res_a(1, "ana@pix.com", "Ana Souza", 1000.00, 10.00, 120.50, -20.00, 869.50, 869.50, 0, "CONCILIADA"),
    res_a(2, "bruno@pix.com", "Bruno Lima", 250.00, 120.50, 10.00, 20.00, 380.50, 380.50, 0, "CONCILIADA"),
    res_a(3, "", "", 0, 0, 0, 0, 0, 0, 0, "ENCERRADA"),
    res_a(4, "carla@pix.com", "Carla Dias", 50.00, 0, 0, 0, 50.00, 50.00, 0, "CONCILIADA"),
]

cenarios = {}

# 1) Dia normal: tudo concilia -> RC 00
cenarios["01-dia-normal"] = (
    [HEADER] + CONTAS_OK + RAZAO + [trailer(3, 11, CRED, DEB)],
    RESULTADO_OK + [res_r(3, 11, 4, 0, 130.50, 130.50, 0, "OK")],
    0)

# 2) Lost update: saldo do Bruno perdeu 20,00 (API 360,50 x razao 380,50) e a conta 5 tem
#    saldo 30,00 sem nenhum lancamento no razao -> 2 divergencias, RC 04
cenarios["02-divergencias"] = (
    [HEADER,
     conta(1, "ana@pix.com", "Ana Souza", 869.50),
     conta(2, "bruno@pix.com", "Bruno Lima", 360.50),
     conta(4, "carla@pix.com", "Carla Dias", 50.00),
     conta(5, "duda@pix.com", "Duda Reis", 30.00)] + RAZAO + [trailer(4, 11, CRED, DEB)],
    [RESULTADO_OK[0],
     res_a(2, "bruno@pix.com", "Bruno Lima", 250.00, 120.50, 10.00, 20.00, 380.50, 360.50, -20.00, "DIVERGENTE"),
     RESULTADO_OK[2], RESULTADO_OK[3],
     res_a(5, "duda@pix.com", "Duda Reis", 0, 0, 0, 0, 0, 30.00, 30.00, "SEM RAZAO"),
     res_r(4, 11, 5, 2, 130.50, 130.50, 4, "DIVERGENTE")],
    4)

# 3) Arquivo truncado: sem trailer -> rejeitado, RC 12
cenarios["03-arquivo-truncado"] = (
    [HEADER] + CONTAS_OK + RAZAO,
    [res_r(3, 11, 0, 0, 0, 0, 12, "REJEITADO", "TRAILER AUSENTE - ARQUIVO TRUNCADO")],
    12)

# 4) Totais de controle nao conferem (trailer diz 12 lancamentos, vieram 11) -> RC 12
cenarios["04-trailer-divergente"] = (
    [HEADER] + CONTAS_OK + RAZAO + [trailer(3, 12, CRED, DEB)],
    [res_r(3, 11, 0, 0, 0, 0, 12, "REJEITADO", "TRAILER: QTD DE LANCAMENTOS NAO CONFERE")],
    12)

# 5) Valor nao numerico num lancamento (layout corrompido) -> RC 12
corrompido = RAZAO[5][:38] + "00000000001X050" + RAZAO[5][53:]
cenarios["05-campo-invalido"] = (
    [HEADER] + CONTAS_OK + RAZAO[:5] + [corrompido] + RAZAO[6:] + [trailer(3, 11, CRED, DEB)],
    [res_r(3, 5, 0, 0, 0, 0, 12, "REJEITADO", "LANCAMENTO COM CAMPO NAO NUMERICO")],
    12)

for nome, (entrada, resultado, rc) in cenarios.items():
    pasta = os.path.join(AQUI, "cenarios", nome)
    os.makedirs(pasta, exist_ok=True)
    assert all(len(l) == 150 for l in entrada), nome
    open(os.path.join(pasta, "MOVIMENTO.DAT"), "w").write("\n".join(entrada) + "\n")
    open(os.path.join(pasta, "RESULTADO.esperado"), "w").write("\n".join(resultado) + "\n")
    open(os.path.join(pasta, "rc.esperado"), "w").write(f"{rc}\n")
    print(f"{nome}: {len(entrada)} registros, RC esperado {rc}")
