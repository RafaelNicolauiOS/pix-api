*> ================================================================
*> RESULTADO - saida de maquina do CONCILIA, lida pelo painel web.
*> Registro de 200 bytes. Um 'A' por conta avaliada e um 'R' (resumo)
*> no final. Posicoes documentadas em docs/batch/layout.js.
*>
*> S9(13)V99 SIGN LEADING SEPARATE = sinal + ou - na primeira posicao,
*> seguido de 15 digitos (2 decimais implicitos).
*> ================================================================
 01  RES-REGISTRO.
     05  RES-TIPO                  PIC X(01).
     05  RES-DADOS                 PIC X(199).
     05  RES-A REDEFINES RES-DADOS.
         10  RES-A-CONTA-ID        PIC 9(10).
         10  RES-A-CHAVE           PIC X(30).
         10  RES-A-TITULAR         PIC X(30).
         10  RES-A-ABERTURA        PIC S9(13)V99 SIGN LEADING SEPARATE.
         10  RES-A-CRED-DIA        PIC 9(13)V99.
         10  RES-A-DEB-DIA         PIC 9(13)V99.
         10  RES-A-POSTERIOR       PIC S9(13)V99 SIGN LEADING SEPARATE.
         10  RES-A-CALCULADO       PIC S9(13)V99 SIGN LEADING SEPARATE.
         10  RES-A-SALDO-API       PIC 9(13)V99.
         10  RES-A-DIFERENCA       PIC S9(13)V99 SIGN LEADING SEPARATE.
         10  RES-A-SITUACAO        PIC X(12).
         10  FILLER                PIC X(08).
     05  RES-R REDEFINES RES-DADOS.
         10  RES-R-DATA-REF        PIC 9(08).
         10  RES-R-GERADO-EM       PIC 9(14).
         10  RES-R-PROCESSADO-EM   PIC 9(14).
         10  RES-R-QTD-CONTAS      PIC 9(09).
         10  RES-R-QTD-LANC        PIC 9(09).
         10  RES-R-QTD-AVALIADAS   PIC 9(09).
         10  RES-R-QTD-DIVERG      PIC 9(09).
         10  RES-R-CRED-DIA        PIC 9(15)V99.
         10  RES-R-DEB-DIA         PIC 9(15)V99.
         10  RES-R-RC              PIC 9(02).
         10  RES-R-SITUACAO        PIC X(10).
         10  RES-R-MOTIVO          PIC X(60).
         10  FILLER                PIC X(21).
