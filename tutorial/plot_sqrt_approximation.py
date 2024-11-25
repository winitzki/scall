import matplotlib.pyplot as plt
import numpy as np

# Find the best quadratic approximation to sqrt(x) for x between 1 and 100.
x = np.linspace(1, 100, 100)

# Calculate the corresponding values of sqrt(x).
y = np.sqrt(x)

# Create a matrix A and a vector b for the least squares problem.
A = np.vstack([np.ones(len(x)), x, x**2]).T
b = y

# Solve the least squares problem.
coeffs = np.linalg.lstsq(A, b)[0]

# Create the quadratic polynomial.
# p = lambda x: coeffs[0] + coeffs[1]*x + coeffs[2]*x**2
x0_p = lambda x : (12.5 + x)/7.0 - x**2/1800.0
#x0_p = lambda x : 1.78 + 0.13436*x - 5.47e-4*x**2
#x0_p = lambda x : (8.0 + x)/7.0 - x**2/1850.0

# Plot the original function and the quadratic approximation.
# plt.plot(x, y, label='sqrt(x)')
# plt.plot(x, x0_p(x), label='quadratic approximation')
# plt.xlabel('x')
# plt.ylabel('y')
# plt.legend()
#plt.show()

# Compute the next approximation using a 2nd order algorithm.
def update(x, a):
  #return -1/16 * (x**3 / a) + 9/16 * (x + a/x) - 1/16 * (a**2 / (x**3))  # This gives 3 digits after 1st update, 12 digits after 2nd update, etc., but it is slower.
  return (0.0 + x + a / x)/2.0
# Compute the first approximation. Input must be between 1 and 100.
def x0(a):
    return   (15.0 + 3 * a) / 15.0 if a <= 16.0 else  (45.0 + a) / 14.0

# Compute the second approximation.
def x1(a):
  return update(x0(a), a)

def x1_p(a):
  return update(x0_p(a), a)


# Compute the number of correct digits in the second approximation.

def prec2(a):
  r1 = x1(a)
  r2 = update(r1, a)
  r = update(r2, a)
  return np.log10(np.abs(a - r * r))

def prec1(a):
  r = x1(a)
  return np.log10(np.abs(a - r*r))

x_init = 1
x_end = 100

x = np.linspace(x_init, x_end, 2*np.abs(x_end-x_init))

y = np.array([-prec1(i) for i in x])

plt.plot(x, y, label="precision")


plt.xlabel("x")
plt.ylabel("f(x)")
plt.title(f"Number of correct decimal digits of x1(a) between {x_init} and {x_end}")

plt.legend()
plt.ylim((0, 10))

plt.grid(True)
plt.show()

