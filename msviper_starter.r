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
minimalSet <- ExpressionSet(assayData=exprs)
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

test1 <- c(unlist(strsplit(caseGroups, ",")))
test2 <- c(unlist(strsplit(controlGroups, ",")))

{
if (bootStrapping == "TRUE")
{
   print(context)
   signature <- bootstrapTtest(dSet, context, test1, test2, verbose = FALSE)
   

}
else
{
   signature <- rowTtest(dSet, context, test1,  test2)
   signature <- (qnorm(signature$p.value/2, lower.tail = FALSE) * sign(signature$statistic))[, 1]
   
}
}

nullmodel <- ttestNull(dSet, context, test1, test2, per = 1000, repos = TRUE, verbose = FALSE)
mrs <- msviper(signature, regul, nullmodel, minsize = as.numeric(minSize), ges.filter = as.logical(gesFilter=="TRUE"), verbose = FALSE)
print(mrs)
print(minSize)
print(gesFilter)

summary(mrs, length(mrs$regul))


if (bootStrapping == "TRUE")
{
   mrs <- bootstrapmsviper(mrs, c(method))
}

mrs_le <- ledge(mrs)



{
if ( shadow == "TRUE" )
{
   mrshadow <- shadow(mrs, regulators = as.numeric(shadowValue), verbose = FALSE)
   write.table(summary(mrshadow, length(mrshadow$regulon))$msviper.results, file=paste(runDir, "result.txt", sep=""), sep="\t")
   write.table(mrshadow$shadow, file=paste(runDir, "shadowPair.txt", sep=""), sep = "\t")
}
else
{
   if (class(signature) == "matrix") signature <- rowMeans(signature)   
   write.table(signature, file=paste(runDir, "signature.txt", sep=""), sep = "\t")
   slist <- mrs$signature
   if (ncol(slist)>0) slist <- rowMeans(slist)
   write.table(slist, file=paste(runDir, "mrsSignature.txt", sep=""), sep = "\t")   
   write.table(summary(mrs, length(mrs$regul)), file=paste(runDir, "result.txt", sep=""), sep="\t")
   write(paste(mrs_le$ledge, sep="\t"), file=paste(runDir, "ledges.txt",sep=""), sep="\t")
   write(names(mrs_le$ledge), file=paste(runDir, "masterRegulons.txt", sep=""), sep="\t")
   write(paste(sapply(mrs$regulon, function(x) {names(x$tfmode)}), sep="\t"), file = paste(runDir, "regulons.txt", sep=""), sep = "\t")  
}
}


print("complete msviper process.")
