export const formatPriceToTwoDecimal = (val: string): string => {
  if (val.includes('.') && val.split('.')[1].length > 2) {
    return parseFloat(val).toFixed(2)
  }
  return val
}
