args <- commandArgs(TRUE)

runDir <- args[1]
exprsFile <- paste(runDir, args[2], sep="")
adjFile <- paste(runDir, args[3], sep="")
phenoFile <- paste(runDir, args[4], sep="")
context <- args[5]
caseGroups <- args[6]
controlGroups <- args[7]
gesFilter <- args[8]
minSize <- args[9]
bootStrapping <- args[10]
method <- args[11]
shadow <- args[12]
shadowValue <- args[13]
userLib <- args[14]

if (!is.na(userLib))  .libPaths(userLib)

library(viper)

exprs <- as.matrix(read.table(exprsFile, header=TRUE, sep="\t", row.names=1, as.is=FALSE, check.names=FALSE))
pData <- read.table(phenoFile, row.names=1, header=TRUE, sep="\t")
all(rownames(pData)==colnames(exprs))
metadata <- data.frame(labelDescription=context)
print(metadata)
phenoData <- new("AnnotatedDataFrame", data=pData, varMetadata=metadata)
print(phenoData)
dSet <- ExpressionSet(assayData=exprs, phenoData=phenoData)
print(dSet)
regul <- aracne2regulon(adjFile, dSet, verbose = FALSE)

print(bootStrapping)

cGroups <- c(unlist(strsplit(caseGroups, ",")))
ctlGroups <- c(unlist(strsplit(controlGroups, ",")))

{
if (bootStrapping == "TRUE")
{
   print(context)
   signature <- bootstrapTtest(dSet, context, cGroups, ctlGroups, verbose = FALSE)
   

}
else
{
   signature <- rowTtest(dSet, context, cGroups,  ctlGroups)
   signature <- (qnorm(signature$p.value/2, lower.tail = FALSE) * sign(signature$statistic))[, 1]
   
