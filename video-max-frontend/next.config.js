/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  rewrites: async () => {
    return {
      fallback: [
        {
          source: '/api/v1/:path*',
          destination: `${process.env.NEXT_PUBLIC_API_BASE}/:path*`,
        },
      ],
    };
  },
};

module.exports = nextConfig;
