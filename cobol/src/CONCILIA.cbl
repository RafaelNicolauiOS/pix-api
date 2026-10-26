*> ================================================================
*> CONCILIA - Conciliacao noturna RAZAO x SALDO da pix-api
*>
*> Entrada : MOVIMENTO  (arquivo exportado por GET /batch/movimento)
*> Saidas  : RELATORIO  (relatorio de 132 colunas, para leitura humana)
*>           RESULTADO  (largura fixa, lido pelo painel web)
*> RETURN-CODE: 00 conciliado | 04 divergencias | 12 arquivo rejeitado
*>              16 erro ao abrir arquivos de saida
*>
*> Fluxo (como num job de mainframe):
*>   1000 abre os arquivos (nomes ligados pelas variaveis DD_*, o
*>        equivalente aos cartoes DD da JCL)
*>   2000 valida o arquivo (header, trailer e totais de controle) e
*>        carrega o cadastro de contas na memoria
*>   3000/4000 SORT dos lancamentos por conta + "balance line":
*>        casa o razao ordenado com o cadastro ordenado, conta a conta
*>   5000/6000 resumo, relatorio e RETURN-CODE
*>
*> Regra: SALDO CALCULADO = ABERTURA (antes do dia)
*>                        + CREDITOS DO DIA - DEBITOS DO DIA
*>                        + MOVIMENTO POSTERIOR (da meia-noite ate a extracao)
*>        e precisa ser IGUAL ao saldo informado pela API.
*> ================================================================
 IDENTIFICATION DIVISION.
 PROGRAM-ID. CONCILIA.
*> Autor: Rafael Nicolau

 ENVIRONMENT DIVISION.
 CONFIGURATION SECTION.
 SPECIAL-NAMES.
*>   Padrao brasileiro nos campos editados: 1.234,56
     DECIMAL-POINT IS COMMA.
 INPUT-OUTPUT SECTION.
 FILE-CONTROL.
     SELECT MOVIMENTO ASSIGN TO "MOVIMENTO"
            ORGANIZATION IS LINE SEQUENTIAL
            FILE STATUS IS WS-FS-MOV.
     SELECT RELATORIO ASSIGN TO "RELATORIO"
            ORGANIZATION IS LINE SEQUENTIAL
            FILE STATUS IS WS-FS-REL.
     SELECT RESULTADO ASSIGN TO "RESULTADO"
            ORGANIZATION IS LINE SEQUENTIAL
            FILE STATUS IS WS-FS-RES.
     SELECT TRABALHO ASSIGN TO "SORTWK".

 DATA DIVISION.
 FILE SECTION.
 FD  MOVIMENTO.
     COPY MOVIMENTO.

 FD  RELATORIO.
 01  REL-LINHA                     PIC X(132).

 FD  RESULTADO.
     COPY RESULTADO.

*> Arquivo de trabalho do SORT: so os campos necessarios dos lancamentos.
 SD  TRABALHO.
 01  SRT-REGISTRO.
     05  SRT-CONTA-ID              PIC 9(10).
     05  SRT-DATA                  PIC 9(08).
     05  SRT-HORA                  PIC 9(06).
     05  SRT-NATUREZA              PIC X(01).
         88  SRT-CREDITO                     VALUE 'C'.
     05  SRT-VALOR                 PIC 9(13)V99.

 WORKING-STORAGE SECTION.
 01  WS-FS-MOV                     PIC X(02).
 01  WS-FS-REL                     PIC X(02).
 01  WS-FS-RES                     PIC X(02).

 01  WS-CHAVES.
     05  WS-FIM-MOV                PIC X(01) VALUE 'N'.
         88  FIM-MOVIMENTO                   VALUE 'S'.
     05  WS-FIM-SORT               PIC X(01) VALUE 'N'.
         88  FIM-SORT                        VALUE 'S'.
     05  WS-SITUACAO-ARQ           PIC X(01) VALUE 'S'.
         88  ARQUIVO-VALIDO                  VALUE 'S'.
         88  ARQUIVO-INVALIDO                VALUE 'N'.
     05  WS-TRAILER-LIDO           PIC X(01) VALUE 'N'.
         88  TRAILER-LIDO                    VALUE 'S'.
     05  WS-ACHOU                  PIC X(01) VALUE 'N'.
         88  ACHOU-CONTA                     VALUE 'S'.

 01  WS-MOTIVO                     PIC X(60) VALUE SPACES.
 01  WS-RC                         PIC 9(02) VALUE 0.
 01  WS-AGORA-ENV                  PIC X(14) VALUE SPACES.
 01  WS-DATA-SISTEMA               PIC X(21).
 01  WS-LIMITE-ID                  PIC 9(10) VALUE 0.

 01  WS-CONTROLE.
     05  WS-DATA-REF               PIC 9(08)     VALUE 0.
     05  WS-GERADO-EM              PIC 9(14)     VALUE 0.
     05  WS-PROCESSADO-EM          PIC 9(14)     VALUE 0.
     05  WS-QTD-CONTAS             PIC 9(09)     VALUE 0.
     05  WS-QTD-LANC               PIC 9(09)     VALUE 0.
     05  WS-TOT-CREDITOS           PIC 9(15)V99  VALUE 0.
     05  WS-TOT-DEBITOS            PIC 9(15)V99  VALUE 0.
     05  WS-QTD-AVALIADAS          PIC 9(09)     VALUE 0.
     05  WS-QTD-DIVERG             PIC 9(09)     VALUE 0.
     05  WS-CRED-DIA-TOTAL         PIC 9(15)V99  VALUE 0.
     05  WS-DEB-DIA-TOTAL          PIC 9(15)V99  VALUE 0.
     05  WS-ID-ANTERIOR            PIC 9(10)     VALUE 0.

*> Totais de controle informados no trailer do arquivo.
 01  WS-TRAILER.
     05  WS-T-QTD-CONTAS           PIC 9(09)     VALUE 0.
     05  WS-T-QTD-LANC             PIC 9(09)     VALUE 0.
     05  WS-T-TOT-CREDITOS         PIC 9(15)V99  VALUE 0.
     05  WS-T-TOT-DEBITOS          PIC 9(15)V99  VALUE 0.

*> Cadastro de contas em memoria, na ordem de id em que vem no arquivo.
*> OCCURS DEPENDING ON: um "array" de tamanho variavel, limitado a 5000.
 01  WS-TABELA.
     05  WS-QTD-TAB                PIC 9(04) VALUE 0.
     05  WS-PROX-TAB               PIC 9(04) VALUE 1.
     05  WS-CONTA OCCURS 0 TO 5000 TIMES DEPENDING ON WS-QTD-TAB.
         10  TAB-ID                PIC 9(10).
         10  TAB-CHAVE             PIC X(77).
         10  TAB-TITULAR           PIC X(30).
         10  TAB-SALDO             PIC 9(13)V99.

*> Conta em processamento (acumuladores da quebra de controle).
 01  WS-CONTA-ATUAL.
     05  AT-CONTA-ID               PIC 9(10).
     05  AT-CHAVE                  PIC X(77).
     05  AT-TITULAR                PIC X(30).
     05  AT-ABERTURA               PIC S9(13)V99.
     05  AT-CRED-DIA               PIC 9(13)V99.
     05  AT-DEB-DIA                PIC 9(13)V99.
     05  AT-POSTERIOR              PIC S9(13)V99.
     05  AT-CALCULADO              PIC S9(13)V99.
     05  AT-SALDO-API              PIC 9(13)V99.
     05  AT-DIFERENCA              PIC S9(13)V99.
     05  AT-SITUACAO               PIC X(12).
         88  AT-DIVERGENTE  VALUES 'DIVERGENTE' 'SEM RAZAO' 'SEM CADASTRO'.

*> Auxiliares de formatacao de data (AAAAMMDD -> DD/MM/AAAA).
 01  WS-DATA-AUX                   PIC 9(08).
 01  WS-DATA-PARTES REDEFINES WS-DATA-AUX.
     05  WS-DP-ANO                 PIC 9(04).
     05  WS-DP-MES                 PIC 9(02).
     05  WS-DP-DIA                 PIC 9(02).
 01  WS-TS-AUX                     PIC 9(14).
 01  WS-TS-PARTES REDEFINES WS-TS-AUX.
     05  WS-TP-ANO                 PIC 9(04).
     05  WS-TP-MES                 PIC 9(02).
     05  WS-TP-DIA                 PIC 9(02).
     05  WS-TP-HORA                PIC 9(02).
     05  WS-TP-MIN                 PIC 9(02).
     05  WS-TP-SEG                 PIC 9(02).
 01  WS-DATA-FMT                   PIC X(10).
 01  WS-TS-FMT                     PIC X(19).

*> Campos editados: a mascara insere pontos, virgula e sinal na impressao.
 01  WS-ED-QTD                     PIC ZZZ.ZZZ.ZZ9.
 01  WS-ED-VALOR                   PIC --.---.---.--9,99.
 01  WS-ROTULO                     PIC X(34).
 01  WS-NUM-QTD                    PIC 9(09).
 01  WS-NUM-VALOR                  PIC S9(15)V99.

*> ---------------------------- relatorio ----------------------------
 01  REL-TRACOS                    PIC X(132) VALUE ALL '-'.
 01  REL-BRANCO                    PIC X(132) VALUE SPACES.

 01  REL-CAB1.
     05  FILLER  PIC X(40) VALUE 'PIX-API - PROCESSAMENTO NOTURNO'.
     05  FILLER  PIC X(80) VALUE 'CONCILIA - CONCILIACAO RAZAO X SALDO'.
     05  FILLER  PIC X(12) VALUE 'PAGINA 0001'.

 01  REL-CAB2.
     05  FILLER  PIC X(20) VALUE 'DATA DE REFERENCIA: '.
     05  CAB2-DATA-REF             PIC X(10).
     05  FILLER  PIC X(13) VALUE '   EXTRACAO: '.
     05  CAB2-GERADO               PIC X(19).
     05  FILLER  PIC X(18) VALUE '   PROCESSAMENTO: '.
     05  CAB2-PROCESSADO           PIC X(19).
     05  FILLER  PIC X(33) VALUE SPACES.

 01  REL-CAB3.
     05  FILLER  PIC X(10) VALUE '     CONTA'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(14) VALUE 'CHAVE PIX'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(14) VALUE '      ABERTURA'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(14) VALUE '  CREDITOS DIA'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(14) VALUE '   DEBITOS DIA'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(14) VALUE '   SALDO CALC.'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(14) VALUE '     SALDO API'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(14) VALUE '     DIFERENCA'.
     05  FILLER  PIC X(01) VALUE SPACE.
     05  FILLER  PIC X(12) VALUE 'SITUACAO'.
     05  FILLER  PIC X(04) VALUE SPACES.

 01  REL-DETALHE.
     05  DET-CONTA                 PIC Z(9)9.
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-CHAVE                 PIC X(14).
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-ABERTURA              PIC ---.---.--9,99.
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-CRED-DIA              PIC ---.---.--9,99.
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-DEB-DIA               PIC ---.---.--9,99.
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-CALCULADO             PIC ---.---.--9,99.
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-SALDO-API             PIC ---.---.--9,99.
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-DIFERENCA             PIC ---.---.--9,99.
     05  FILLER                    PIC X(01) VALUE SPACE.
     05  DET-SITUACAO              PIC X(12).
     05  FILLER                    PIC X(04) VALUE SPACES.

 01  REL-TITULO.
     05  TIT-TEXTO                 PIC X(132).

 01  REL-TOTAL.
     05  FILLER                    PIC X(02) VALUE SPACES.
     05  TOT-ROTULO                PIC X(34).
     05  FILLER                    PIC X(02) VALUE ': '.
     05  TOT-VALOR                 PIC X(20) JUSTIFIED RIGHT.
     05  FILLER                    PIC X(74) VALUE SPACES.

 01  REL-RESULTADO.
     05  FILLER                    PIC X(11) VALUE 'RESULTADO: '.
     05  RESULT-TEXTO              PIC X(100).
     05  FILLER                    PIC X(03) VALUE 'RC='.
     05  RESULT-RC                 PIC 9(02).
     05  FILLER                    PIC X(16) VALUE SPACES.

 PROCEDURE DIVISION.
 0000-PRINCIPAL.
     PERFORM 1000-INICIAR
     IF ARQUIVO-VALIDO
         PERFORM 2000-VALIDAR-E-CARREGAR
     END-IF
     PERFORM 2900-IMPRIMIR-CABECALHO
     IF ARQUIVO-VALIDO
         SORT TRABALHO
              ON ASCENDING KEY SRT-CONTA-ID SRT-DATA SRT-HORA
              INPUT PROCEDURE  IS 3000-SELECIONAR-LANCAMENTOS
              OUTPUT PROCEDURE IS 4000-CONCILIAR
         PERFORM 5000-ENCERRAR-CONCILIACAO
     ELSE
         PERFORM 6000-REJEITAR-ARQUIVO
     END-IF
     PERFORM 9000-FINALIZAR
     STOP RUN.

*> ------------------------------------------------------------------
 1000-INICIAR.
     DISPLAY 'CONCILIA - INICIO DO PROCESSAMENTO'
*>   CONCILIA_AGORA (AAAAMMDDhhmmss) fixa o horario: deixa os testes
*>   deterministicos. Sem ela, usa o relogio do sistema.
     ACCEPT WS-AGORA-ENV FROM ENVIRONMENT 'CONCILIA_AGORA'
     IF WS-AGORA-ENV IS NUMERIC
         MOVE WS-AGORA-ENV TO WS-PROCESSADO-EM
     ELSE
         MOVE FUNCTION CURRENT-DATE TO WS-DATA-SISTEMA
         MOVE WS-DATA-SISTEMA(1:14) TO WS-PROCESSADO-EM
     END-IF

     OPEN OUTPUT RELATORIO
     OPEN OUTPUT RESULTADO
     IF WS-FS-REL NOT = '00' OR WS-FS-RES NOT = '00'
         DISPLAY 'CONCILIA - ERRO AO ABRIR SAIDAS: RELATORIO FS='
                 WS-FS-REL ' RESULTADO FS=' WS-FS-RES
         MOVE 16 TO RETURN-CODE
         STOP RUN
     END-IF

     OPEN INPUT MOVIMENTO
     IF WS-FS-MOV NOT = '00'
         STRING 'NAO FOI POSSIVEL ABRIR O MOVIMENTO (FILE STATUS '
                WS-FS-MOV ')' DELIMITED BY SIZE INTO WS-MOTIVO
         SET ARQUIVO-INVALIDO TO TRUE
     END-IF.

*> ------------------------------------------------------------------
*> Primeira leitura: valida a estrutura e os totais de controle e
*> carrega o cadastro. Nada e conciliado se o arquivo nao for integro.
 2000-VALIDAR-E-CARREGAR.
     PERFORM 2100-LER-MOVIMENTO
     EVALUATE TRUE
         WHEN FIM-MOVIMENTO
             MOVE 'ARQUIVO VAZIO' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN NOT MOV-HEADER
             MOVE 'PRIMEIRO REGISTRO NAO E O HEADER' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN MOV-H-DATA-REF NOT NUMERIC OR MOV-H-GERADO-EM NOT NUMERIC
             MOVE 'HEADER COM DATA INVALIDA' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN OTHER
             MOVE MOV-H-DATA-REF  TO WS-DATA-REF
             MOVE MOV-H-GERADO-EM TO WS-GERADO-EM
             PERFORM 2100-LER-MOVIMENTO
     END-EVALUATE

     PERFORM UNTIL FIM-MOVIMENTO OR ARQUIVO-INVALIDO
         IF TRAILER-LIDO
             MOVE 'REGISTRO APOS O TRAILER' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         ELSE
             EVALUATE TRUE
                 WHEN MOV-CONTA
                     PERFORM 2200-CARREGAR-CONTA
                 WHEN MOV-LANCAMENTO
                     PERFORM 2300-CONFERIR-LANCAMENTO
                 WHEN MOV-TRAILER
                     PERFORM 2400-GUARDAR-TRAILER
                 WHEN MOV-HEADER
                     MOVE 'HEADER DUPLICADO' TO WS-MOTIVO
                     SET ARQUIVO-INVALIDO TO TRUE
                 WHEN OTHER
                     STRING 'TIPO DE REGISTRO DESCONHECIDO: ' MOV-TIPO
                            DELIMITED BY SIZE INTO WS-MOTIVO
                     SET ARQUIVO-INVALIDO TO TRUE
             END-EVALUATE
             PERFORM 2100-LER-MOVIMENTO
         END-IF
     END-PERFORM

     IF ARQUIVO-VALIDO
         PERFORM 2500-CONFERIR-TOTAIS-DE-CONTROLE
     END-IF
     CLOSE MOVIMENTO

     IF ARQUIVO-VALIDO
         MOVE WS-DATA-REF TO WS-DATA-AUX
         PERFORM 8100-FORMATAR-DATA
         MOVE WS-QTD-CONTAS TO WS-ED-QTD
         DISPLAY 'CONCILIA - ARQUIVO VALIDADO: DATA DE REFERENCIA '
                 WS-DATA-FMT ', ' FUNCTION TRIM(WS-ED-QTD) ' CONTAS'
         MOVE WS-QTD-LANC TO WS-ED-QTD
         DISPLAY 'CONCILIA - ' FUNCTION TRIM(WS-ED-QTD)
                 ' LANCAMENTOS NO RAZAO - TOTAIS DE CONTROLE CONFEREM'
     END-IF.

 2100-LER-MOVIMENTO.
     READ MOVIMENTO
         AT END SET FIM-MOVIMENTO TO TRUE
     END-READ.

 2200-CARREGAR-CONTA.
     EVALUATE TRUE
         WHEN MOV-C-CONTA-ID NOT NUMERIC OR MOV-C-SALDO NOT NUMERIC
             MOVE 'CONTA COM CAMPO NAO NUMERICO' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN MOV-C-CONTA-ID NOT > WS-ID-ANTERIOR
             MOVE 'CONTAS FORA DE ORDEM OU DUPLICADAS' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN WS-QTD-TAB = 5000
             MOVE 'MAIS DE 5000 CONTAS NO ARQUIVO' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN OTHER
             ADD 1 TO WS-QTD-TAB
             ADD 1 TO WS-QTD-CONTAS
             MOVE MOV-C-CONTA-ID TO TAB-ID(WS-QTD-TAB) WS-ID-ANTERIOR
             MOVE MOV-C-CHAVE    TO TAB-CHAVE(WS-QTD-TAB)
             MOVE MOV-C-TITULAR  TO TAB-TITULAR(WS-QTD-TAB)
             MOVE MOV-C-SALDO    TO TAB-SALDO(WS-QTD-TAB)
     END-EVALUATE.

 2300-CONFERIR-LANCAMENTO.
     EVALUATE TRUE
         WHEN MOV-L-CONTA-ID NOT NUMERIC OR MOV-L-DATA NOT NUMERIC
           OR MOV-L-HORA NOT NUMERIC     OR MOV-L-VALOR NOT NUMERIC
             MOVE 'LANCAMENTO COM CAMPO NAO NUMERICO' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN NOT MOV-L-CREDITO AND NOT MOV-L-DEBITO
             MOVE 'LANCAMENTO COM NATUREZA INVALIDA' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN MOV-L-CREDITO
             ADD 1 TO WS-QTD-LANC
             ADD MOV-L-VALOR TO WS-TOT-CREDITOS
         WHEN OTHER
             ADD 1 TO WS-QTD-LANC
             ADD MOV-L-VALOR TO WS-TOT-DEBITOS
     END-EVALUATE.

 2400-GUARDAR-TRAILER.
     IF MOV-T-QTD-CONTAS NOT NUMERIC OR MOV-T-QTD-LANC NOT NUMERIC
        OR MOV-T-TOT-CREDITOS NOT NUMERIC
        OR MOV-T-TOT-DEBITOS NOT NUMERIC
         MOVE 'TRAILER COM CAMPO NAO NUMERICO' TO WS-MOTIVO
         SET ARQUIVO-INVALIDO TO TRUE
     ELSE
         SET TRAILER-LIDO TO TRUE
         MOVE MOV-T-QTD-CONTAS   TO WS-T-QTD-CONTAS
         MOVE MOV-T-QTD-LANC     TO WS-T-QTD-LANC
         MOVE MOV-T-TOT-CREDITOS TO WS-T-TOT-CREDITOS
         MOVE MOV-T-TOT-DEBITOS  TO WS-T-TOT-DEBITOS
     END-IF.

*> Totais de controle: o que foi lido precisa bater com o que o
*> sistema de origem declarou ter enviado. Pega arquivo truncado,
*> duplicado ou corrompido no caminho.
 2500-CONFERIR-TOTAIS-DE-CONTROLE.
     EVALUATE TRUE
         WHEN NOT TRAILER-LIDO
             MOVE 'TRAILER AUSENTE - ARQUIVO TRUNCADO' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN WS-T-QTD-CONTAS NOT = WS-QTD-CONTAS
             MOVE 'TRAILER: QTD DE CONTAS NAO CONFERE' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN WS-T-QTD-LANC NOT = WS-QTD-LANC
             MOVE 'TRAILER: QTD DE LANCAMENTOS NAO CONFERE' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN WS-T-TOT-CREDITOS NOT = WS-TOT-CREDITOS
             MOVE 'TRAILER: TOTAL DE CREDITOS NAO CONFERE' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
         WHEN WS-T-TOT-DEBITOS NOT = WS-TOT-DEBITOS
             MOVE 'TRAILER: TOTAL DE DEBITOS NAO CONFERE' TO WS-MOTIVO
             SET ARQUIVO-INVALIDO TO TRUE
     END-EVALUATE.

 2900-IMPRIMIR-CABECALHO.
     MOVE WS-DATA-REF TO WS-DATA-AUX
     PERFORM 8100-FORMATAR-DATA
     MOVE WS-DATA-FMT TO CAB2-DATA-REF
     MOVE WS-GERADO-EM TO WS-TS-AUX
     PERFORM 8200-FORMATAR-DATA-HORA
     MOVE WS-TS-FMT TO CAB2-GERADO
     MOVE WS-PROCESSADO-EM TO WS-TS-AUX
     PERFORM 8200-FORMATAR-DATA-HORA
     MOVE WS-TS-FMT TO CAB2-PROCESSADO

     WRITE REL-LINHA FROM REL-CAB1
     WRITE REL-LINHA FROM REL-CAB2
     WRITE REL-LINHA FROM REL-TRACOS
     IF ARQUIVO-VALIDO
         WRITE REL-LINHA FROM REL-CAB3
         WRITE REL-LINHA FROM REL-TRACOS
     END-IF.

*> ------------------------------------------------------------------
*> INPUT PROCEDURE do SORT: segunda leitura, entrega so os lancamentos.
 3000-SELECIONAR-LANCAMENTOS.
     MOVE 'N' TO WS-FIM-MOV
     OPEN INPUT MOVIMENTO
     PERFORM 2100-LER-MOVIMENTO
     PERFORM UNTIL FIM-MOVIMENTO
         IF MOV-LANCAMENTO
             MOVE MOV-L-CONTA-ID TO SRT-CONTA-ID
             MOVE MOV-L-DATA     TO SRT-DATA
             MOVE MOV-L-HORA     TO SRT-HORA
             MOVE MOV-L-NATUREZA TO SRT-NATUREZA
             MOVE MOV-L-VALOR    TO SRT-VALOR
             RELEASE SRT-REGISTRO
         END-IF
         PERFORM 2100-LER-MOVIMENTO
     END-PERFORM
     CLOSE MOVIMENTO.

*> ------------------------------------------------------------------
*> OUTPUT PROCEDURE do SORT: "balance line". O razao chega ordenado por
*> conta; o cadastro ja esta ordenado por id. Os dois sao percorridos
*> juntos, como dois ponteiros andando em listas ordenadas.
 4000-CONCILIAR.
     PERFORM 4100-OBTER-LANCAMENTO
     PERFORM UNTIL FIM-SORT
*>       Contas do cadastro com id menor que o atual nao tem razao.
         MOVE SRT-CONTA-ID TO WS-LIMITE-ID
         PERFORM 4400-EMITIR-CONTAS-SEM-RAZAO

         INITIALIZE WS-CONTA-ATUAL
         MOVE SRT-CONTA-ID TO AT-CONTA-ID
*>       Quebra de controle: acumula enquanto a conta nao muda.
         PERFORM UNTIL FIM-SORT OR SRT-CONTA-ID NOT = AT-CONTA-ID
             PERFORM 4200-CLASSIFICAR-LANCAMENTO
             PERFORM 4100-OBTER-LANCAMENTO
         END-PERFORM
         PERFORM 4300-FECHAR-CONTA
     END-PERFORM
*>   O que sobrou do cadastro tambem nao tem razao.
     MOVE 9999999999 TO WS-LIMITE-ID
     PERFORM 4400-EMITIR-CONTAS-SEM-RAZAO.

 4100-OBTER-LANCAMENTO.
     RETURN TRABALHO
         AT END SET FIM-SORT TO TRUE
     END-RETURN.

 4200-CLASSIFICAR-LANCAMENTO.
     EVALUATE TRUE
         WHEN SRT-DATA < WS-DATA-REF
             IF SRT-CREDITO
                 ADD SRT-VALOR TO AT-ABERTURA
             ELSE
                 SUBTRACT SRT-VALOR FROM AT-ABERTURA
             END-IF
         WHEN SRT-DATA = WS-DATA-REF
             IF SRT-CREDITO
                 ADD SRT-VALOR TO AT-CRED-DIA
             ELSE
                 ADD SRT-VALOR TO AT-DEB-DIA
             END-IF
         WHEN OTHER
             IF SRT-CREDITO
                 ADD SRT-VALOR TO AT-POSTERIOR
             ELSE
                 SUBTRACT SRT-VALOR FROM AT-POSTERIOR
             END-IF
     END-EVALUATE.

*> Conta que tem razao: compara o saldo calculado com o da API.
 4300-FECHAR-CONTA.
     COMPUTE AT-CALCULADO = AT-ABERTURA + AT-CRED-DIA - AT-DEB-DIA
                          + AT-POSTERIOR
     MOVE 'N' TO WS-ACHOU
     IF WS-PROX-TAB NOT > WS-QTD-TAB
         IF TAB-ID(WS-PROX-TAB) = AT-CONTA-ID
             MOVE 'S' TO WS-ACHOU
         END-IF
     END-IF

     IF ACHOU-CONTA
         MOVE TAB-CHAVE(WS-PROX-TAB)   TO AT-CHAVE
         MOVE TAB-TITULAR(WS-PROX-TAB) TO AT-TITULAR
         MOVE TAB-SALDO(WS-PROX-TAB)   TO AT-SALDO-API
         ADD 1 TO WS-PROX-TAB
         COMPUTE AT-DIFERENCA = AT-SALDO-API - AT-CALCULADO
         IF AT-DIFERENCA = 0
             MOVE 'CONCILIADA' TO AT-SITUACAO
         ELSE
             MOVE 'DIVERGENTE' TO AT-SITUACAO
         END-IF
     ELSE
*>       Conta excluida: o razao continua, e precisa estar zerado.
         MOVE 0 TO AT-SALDO-API
         COMPUTE AT-DIFERENCA = 0 - AT-CALCULADO
         IF AT-CALCULADO = 0
             MOVE 'ENCERRADA' TO AT-SITUACAO
         ELSE
             MOVE 'SEM CADASTRO' TO AT-SITUACAO
         END-IF
     END-IF
     PERFORM 4800-REGISTRAR-RESULTADO.

*> Contas do cadastro sem nenhum lancamento, com id abaixo do limite.
 4400-EMITIR-CONTAS-SEM-RAZAO.
     PERFORM UNTIL WS-PROX-TAB > WS-QTD-TAB
         IF TAB-ID(WS-PROX-TAB) NOT < WS-LIMITE-ID
             EXIT PERFORM
         END-IF
         INITIALIZE WS-CONTA-ATUAL
         MOVE TAB-ID(WS-PROX-TAB)      TO AT-CONTA-ID
         MOVE TAB-CHAVE(WS-PROX-TAB)   TO AT-CHAVE
         MOVE TAB-TITULAR(WS-PROX-TAB) TO AT-TITULAR
         MOVE TAB-SALDO(WS-PROX-TAB)   TO AT-SALDO-API
         MOVE AT-SALDO-API             TO AT-DIFERENCA
         IF AT-SALDO-API = 0
             MOVE 'CONCILIADA' TO AT-SITUACAO
         ELSE
             MOVE 'SEM RAZAO' TO AT-SITUACAO
         END-IF
         PERFORM 4800-REGISTRAR-RESULTADO
         ADD 1 TO WS-PROX-TAB
     END-PERFORM.

*> Grava a conta no RESULTADO e no RELATORIO e acumula os totais.
 4800-REGISTRAR-RESULTADO.
     ADD 1 TO WS-QTD-AVALIADAS
     IF AT-DIVERGENTE
         ADD 1 TO WS-QTD-DIVERG
         DISPLAY 'CONCILIA - CONTA ' AT-CONTA-ID ' '
                 FUNCTION TRIM(AT-SITUACAO)
     END-IF
     ADD AT-CRED-DIA TO WS-CRED-DIA-TOTAL
     ADD AT-DEB-DIA  TO WS-DEB-DIA-TOTAL

     MOVE SPACES          TO RES-REGISTRO
     MOVE 'A'             TO RES-TIPO
     MOVE AT-CONTA-ID     TO RES-A-CONTA-ID
     MOVE AT-CHAVE        TO RES-A-CHAVE
     MOVE AT-TITULAR      TO RES-A-TITULAR
     MOVE AT-ABERTURA     TO RES-A-ABERTURA
     MOVE AT-CRED-DIA     TO RES-A-CRED-DIA
     MOVE AT-DEB-DIA      TO RES-A-DEB-DIA
     MOVE AT-POSTERIOR    TO RES-A-POSTERIOR
     MOVE AT-CALCULADO    TO RES-A-CALCULADO
     MOVE AT-SALDO-API    TO RES-A-SALDO-API
     MOVE AT-DIFERENCA    TO RES-A-DIFERENCA
     MOVE AT-SITUACAO     TO RES-A-SITUACAO
     WRITE RES-REGISTRO

     MOVE AT-CONTA-ID     TO DET-CONTA
     MOVE AT-CHAVE        TO DET-CHAVE
     MOVE AT-ABERTURA     TO DET-ABERTURA
     MOVE AT-CRED-DIA     TO DET-CRED-DIA
     MOVE AT-DEB-DIA      TO DET-DEB-DIA
     MOVE AT-CALCULADO    TO DET-CALCULADO
     MOVE AT-SALDO-API    TO DET-SALDO-API
     MOVE AT-DIFERENCA    TO DET-DIFERENCA
     MOVE AT-SITUACAO     TO DET-SITUACAO
     WRITE REL-LINHA FROM REL-DETALHE.

*> ------------------------------------------------------------------
 5000-ENCERRAR-CONCILIACAO.
     IF WS-QTD-DIVERG = 0
         MOVE 0 TO WS-RC
         MOVE 'CONCILIACAO OK - RAZAO E SALDOS CONFEREM' TO RESULT-TEXTO
     ELSE
         MOVE 4 TO WS-RC
         MOVE 'CONCILIACAO COM DIVERGENCIAS - VERIFICAR AS CONTAS ACIMA'
           TO RESULT-TEXTO
     END-IF
     PERFORM 7000-GRAVAR-RESUMO
     PERFORM 7100-IMPRIMIR-TOTAIS.

 6000-REJEITAR-ARQUIVO.
     MOVE 12 TO WS-RC
     DISPLAY 'CONCILIA - ARQUIVO REJEITADO: ' FUNCTION TRIM(WS-MOTIVO)
     MOVE SPACES TO TIT-TEXTO
     WRITE REL-LINHA FROM REL-BRANCO
     STRING '*** ARQUIVO REJEITADO: ' FUNCTION TRIM(WS-MOTIVO) ' ***'
            DELIMITED BY SIZE INTO TIT-TEXTO
     WRITE REL-LINHA FROM REL-TITULO
     MOVE 'NENHUMA CONTA FOI CONCILIADA. CORRIJA A ORIGEM E REPROCESSE.'
       TO TIT-TEXTO
     WRITE REL-LINHA FROM REL-TITULO
     WRITE REL-LINHA FROM REL-BRANCO
     STRING 'ARQUIVO REJEITADO - ' FUNCTION TRIM(WS-MOTIVO)
            DELIMITED BY SIZE INTO RESULT-TEXTO
     PERFORM 7000-GRAVAR-RESUMO
     PERFORM 7100-IMPRIMIR-TOTAIS.

*> Registro 'R' (resumo), o ultimo do RESULTADO.
 7000-GRAVAR-RESUMO.
     MOVE SPACES             TO RES-REGISTRO
     MOVE 'R'                TO RES-TIPO
     MOVE WS-DATA-REF        TO RES-R-DATA-REF
     MOVE WS-GERADO-EM       TO RES-R-GERADO-EM
     MOVE WS-PROCESSADO-EM   TO RES-R-PROCESSADO-EM
     MOVE WS-QTD-CONTAS      TO RES-R-QTD-CONTAS
     MOVE WS-QTD-LANC        TO RES-R-QTD-LANC
     MOVE WS-QTD-AVALIADAS   TO RES-R-QTD-AVALIADAS
     MOVE WS-QTD-DIVERG      TO RES-R-QTD-DIVERG
     MOVE WS-CRED-DIA-TOTAL  TO RES-R-CRED-DIA
     MOVE WS-DEB-DIA-TOTAL   TO RES-R-DEB-DIA
     MOVE WS-RC              TO RES-R-RC
     EVALUATE WS-RC
         WHEN 0     MOVE 'OK'         TO RES-R-SITUACAO
         WHEN 4     MOVE 'DIVERGENTE' TO RES-R-SITUACAO
         WHEN OTHER MOVE 'REJEITADO'  TO RES-R-SITUACAO
     END-EVALUATE
     MOVE WS-MOTIVO          TO RES-R-MOTIVO
     WRITE RES-REGISTRO.

 7100-IMPRIMIR-TOTAIS.
     WRITE REL-LINHA FROM REL-TRACOS
     MOVE 'TOTAIS DE CONTROLE' TO TIT-TEXTO
     WRITE REL-LINHA FROM REL-TITULO

     MOVE 'REGISTROS DE CONTA LIDOS' TO WS-ROTULO
     MOVE WS-QTD-CONTAS TO WS-NUM-QTD
     PERFORM 7210-LINHA-QUANTIDADE
     MOVE 'LANCAMENTOS LIDOS NO RAZAO' TO WS-ROTULO
     MOVE WS-QTD-LANC TO WS-NUM-QTD
     PERFORM 7210-LINHA-QUANTIDADE
     MOVE 'CREDITOS NO ARQUIVO' TO WS-ROTULO
     MOVE WS-TOT-CREDITOS TO WS-NUM-VALOR
     PERFORM 7220-LINHA-VALOR
     MOVE 'DEBITOS NO ARQUIVO' TO WS-ROTULO
     MOVE WS-TOT-DEBITOS TO WS-NUM-VALOR
     PERFORM 7220-LINHA-VALOR
     MOVE 'CREDITOS DO DIA' TO WS-ROTULO
     MOVE WS-CRED-DIA-TOTAL TO WS-NUM-VALOR
     PERFORM 7220-LINHA-VALOR
     MOVE 'DEBITOS DO DIA' TO WS-ROTULO
     MOVE WS-DEB-DIA-TOTAL TO WS-NUM-VALOR
     PERFORM 7220-LINHA-VALOR
     MOVE 'CONTAS AVALIADAS' TO WS-ROTULO
     MOVE WS-QTD-AVALIADAS TO WS-NUM-QTD
     PERFORM 7210-LINHA-QUANTIDADE
     MOVE 'CONTAS COM DIVERGENCIA' TO WS-ROTULO
     MOVE WS-QTD-DIVERG TO WS-NUM-QTD
     PERFORM 7210-LINHA-QUANTIDADE

     WRITE REL-LINHA FROM REL-TRACOS
     MOVE WS-RC TO RESULT-RC
     WRITE REL-LINHA FROM REL-RESULTADO
     IF ARQUIVO-VALIDO
         MOVE 'SALDO CALC. = ABERTURA + CREDITOS DO DIA - DEBITOS DO DIA + MOVIMENTO POSTERIOR AO DIA (ATE A EXTRACAO)'
           TO TIT-TEXTO
         WRITE REL-LINHA FROM REL-TITULO
     END-IF.

*> Rotulo seguido de pontos ate a coluna do valor: "CONTAS ....... :"
 7210-LINHA-QUANTIDADE.
     MOVE ALL '.' TO TOT-ROTULO
     STRING FUNCTION TRIM(WS-ROTULO) ' ' DELIMITED BY SIZE INTO TOT-ROTULO
     MOVE WS-NUM-QTD TO WS-ED-QTD
     MOVE WS-ED-QTD TO TOT-VALOR
     WRITE REL-LINHA FROM REL-TOTAL.

 7220-LINHA-VALOR.
     MOVE ALL '.' TO TOT-ROTULO
     STRING FUNCTION TRIM(WS-ROTULO) ' ' DELIMITED BY SIZE INTO TOT-ROTULO
     MOVE WS-NUM-VALOR TO WS-ED-VALOR
     MOVE WS-ED-VALOR TO TOT-VALOR
     WRITE REL-LINHA FROM REL-TOTAL.

*> ------------------------------------------------------------------
 8100-FORMATAR-DATA.
     IF WS-DATA-AUX = 0
         MOVE '--/--/----' TO WS-DATA-FMT
     ELSE
         STRING WS-DP-DIA '/' WS-DP-MES '/' WS-DP-ANO
                DELIMITED BY SIZE INTO WS-DATA-FMT
     END-IF.

 8200-FORMATAR-DATA-HORA.
     IF WS-TS-AUX = 0
         MOVE '--/--/---- --:--:--' TO WS-TS-FMT
     ELSE
         STRING WS-TP-DIA '/' WS-TP-MES '/' WS-TP-ANO ' '
                WS-TP-HORA ':' WS-TP-MIN ':' WS-TP-SEG
                DELIMITED BY SIZE INTO WS-TS-FMT
     END-IF.

*> ------------------------------------------------------------------
 9000-FINALIZAR.
     CLOSE RELATORIO
     CLOSE RESULTADO
     MOVE WS-QTD-AVALIADAS TO WS-ED-QTD
     DISPLAY 'CONCILIA - CONTAS AVALIADAS: ' FUNCTION TRIM(WS-ED-QTD)
     MOVE WS-QTD-DIVERG TO WS-ED-QTD
     DISPLAY 'CONCILIA - CONTAS COM DIVERGENCIA: '
             FUNCTION TRIM(WS-ED-QTD)
     DISPLAY 'CONCILIA - TERMINO DO PROCESSAMENTO - RC=' WS-RC
     MOVE WS-RC TO RETURN-CODE.
